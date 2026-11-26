#!/bin/bash
# Glowfin Tower health (Ben's picks, 2026-11-22). Runs on Tower itself (not in a Docker app), so it keeps working while
# the drives are stopped and can see the hardware directly. Read-only, apart from the three things Ben switched on:
#   * Black box: a reading every 10 seconds (temperatures, Arc load and power, memory, UPS, what's busy), saved with
#     fsync so it survives a crash. Lives in appdata/benflix-health (7 days kept), or in /tmp while the drives are stopped.
#   * Crash check: at each start it works out whether the last stop was clean. If not, it notes the crash on the USB
#     stick (boots.jsonl), tells Ben through Unraid's notifications (email when that's set up in Unraid), and
#     (when every drive is there) starts the drives after 3 minutes so Glowfin comes back by itself.
#   * Freeze timer: the board's own watchdog (iTCO_wdt). A tiny loop pats it every 20 seconds; if Tower freezes hard,
#     the loop stops and the board restarts Tower after 3 minutes. A clean shutdown switches it off first.
# Settings (on/off) live on the USB stick in /boot/config/benflix/health/settings.cfg; Glowfin's Server page writes them.
#
#   health.sh run      every minute from cron (benflix-health.cron)
#   health.sh stop     from /boot/config/stop on a clean shutdown
#   health.sh pinger   the freeze timer's loop (started by run)
#   health.sh install  copies itself to the USB stick and sets up cron + the shutdown hook (the setup command calls it)

FLASH=/boot/config/benflix/health
BB=/mnt/user/appdata/benflix-health
TMPD=/tmp/benflix-health
SELF=/boot/config/benflix/health.sh
PIDF=/var/run/benflix-watchdog.pid
VAR=/var/local/emhttp/var.ini
NOTIFY=/usr/local/emhttp/webGui/scripts/notify

mkdir -p "$FLASH" "$TMPD" 2>/dev/null

setting() { # setting <name> <default>
  local v
  v=$(sed -n "s/^$1=//p" "$FLASH/settings.cfg" 2>/dev/null | tail -1)
  echo "${v:-$2}"
}
var() { sed -n "s/^$1=\"\{0,1\}\([^\"]*\)\"\{0,1\}$/\1/p" "$VAR" 2>/dev/null | head -1; }
note() { echo "$(date '+%F %T') $*" >>"$TMPD/health.log"; }
tell() { # tell <alert|warning|normal> <subject> <words>
  [ -x "$NOTIFY" ] && "$NOTIFY" -e "Glowfin Tower health" -s "$2" -d "$3" -i "$1" -l "/Main" >/dev/null 2>&1
  note "told Ben: $2"
}
num() { case "$1" in '' | *[!0-9.-]*) echo null ;; *) echo "$1" ;; esac; }
jstr() { printf '"%s"' "$(printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | tr -d '\n\r\t')"; }
hwmon() { # hwmon <name> -> its folder
  local h
  for h in /sys/class/hwmon/hwmon*; do [ "$(cat "$h/name" 2>/dev/null)" = "$1" ] && { echo "$h"; return; }; done
}
milli() { [ -r "$1" ] && awk '{printf "%d", $1/1000}' "$1" 2>/dev/null; }
boot_id() { cat /proc/sys/kernel/random/boot_id 2>/dev/null; }
# Where the black box goes: appdata on a fast pool (like /mnt/cache) when there is one, so a reading every 10 seconds
# never keeps a hard drive spinning. Empty while the drives are stopped (then /tmp is used).
bb_dir() {
  local p
  for p in /mnt/*/appdata; do
    case "$p" in /mnt/user/* | /mnt/user0/* | /mnt/disk[0-9]*/* | /mnt/remotes/* | /mnt/addons/* | /mnt/rootshare/* | /mnt/disks/*) continue ;; esac
    mountpoint -q "${p%/appdata}" 2>/dev/null && mkdir -p "$p/benflix-health" 2>/dev/null && { echo "$p/benflix-health"; return; }
  done
  mountpoint -q /mnt/user 2>/dev/null && mkdir -p "$BB" 2>/dev/null && echo "$BB"
}
booted_at() { echo $(($(date +%s) - $(cut -d. -f1 /proc/uptime))); }

# --------------------------------------------------------------------------- crash check (once per start)

boot_check() {
  [ -f "$TMPD/booted" ] && return
  local bid prev_bid prev_at clean bert log_end prevlog now_at
  bid=$(boot_id)
  now_at=$(booted_at)
  read -r prev_bid prev_at <"$FLASH/boot-current" 2>/dev/null
  if [ -n "$prev_bid" ] && [ "$prev_bid" != "$bid" ]; then
    clean=0
    grep -q "^$prev_bid" "$FLASH/clean-stop" 2>/dev/null && clean=1
    prevlog=/boot/logs/syslog-previous
    log_end=null
    # The log Unraid saved on the USB stick before this start, when it belongs to the last run
    if [ -f "$prevlog" ] && [ "$(stat -c %Y "$prevlog")" -ge "${prev_at:-0}" ]; then
      [ "$clean" = 0 ] && tail -n 40 "$prevlog" | grep -Eqi "Switching to runlevel: [06]|shutting down for system|shutdown\[[0-9]+\]|rc\.(0|6)|Stopping .*(array|services)|unmounting|powerdown" && clean=1
      log_end=$(date -d "$(tail -n 1 "$prevlog" | cut -c1-15)" +%s 2>/dev/null || echo null)
    fi
    bert=0
    dmesg 2>/dev/null | grep -q "BERT: \[Hardware Error\]" && bert=1
    printf '{"boot":%s,"prev_boot":%s,"clean":%s,"bert":%s,"log_end":%s}\n' "$now_at" "$(num "$prev_at")" "$clean" "$bert" "$(num "$log_end")" >>"$FLASH/boots.jsonl"
    tail -n 200 "$FLASH/boots.jsonl" >"$FLASH/boots.tmp" && mv "$FLASH/boots.tmp" "$FLASH/boots.jsonl"
    if [ "$clean" = 0 ]; then
      touch "$TMPD/crashed"
      note "the last stop wasn't clean (board's crash record: $bert)"
      if [ "$(setting crashnote on)" = on ]; then
        tell alert "Tower restarted after a crash" "Tower stopped without shutting down and has started again. Glowfin's Server page > Health shows the last minute before it."
      fi
    fi
  fi
  echo "$bid $now_at" >"$FLASH/boot-current"
  rm -f "$FLASH/clean-stop"
  touch "$TMPD/booted"
}

# After a crash Unraid leaves the drives stopped. Start them when every drive is there, 3 minutes after the start.
drives_after_crash() {
  [ -f "$TMPD/crashed" ] && [ ! -f "$TMPD/drives-done" ] || return
  [ "$(cut -d. -f1 /proc/uptime)" -ge 180 ] || return
  local state missing disabled invalid
  state=$(var mdState)
  if [ "$state" = STARTED ]; then touch "$TMPD/drives-done"; return; fi
  [ "$state" = STOPPED ] || { [ -n "$state" ] && touch "$TMPD/drives-done" && tell warning "Tower's drives are stopped" "Unraid says the drives are '$state', so Glowfin left them alone. Open Unraid > Main to look."; return; }
  missing=$(var mdNumMissing); disabled=$(var mdNumDisabled); invalid=$(var mdNumInvalid)
  touch "$TMPD/drives-done"
  if [ "$(setting autostart on)" = on ] && [ "$missing" = 0 ] && [ "$disabled" = 0 ] && [ "$invalid" = 0 ] && [ -x /usr/local/sbin/emcmd ]; then
    note "starting the drives after the crash"
    if /usr/local/sbin/emcmd cmdStart=Start >/dev/null 2>&1; then
      echo "$(date +%s)" >"$TMPD/drives-started"
      tell normal "Tower started its drives again" "After the crash every drive was there, so Glowfin started them. Unraid may run a parity check now, that's normal."
    else
      tell warning "Tower's drives are stopped" "Glowfin tried to start them after the crash and Unraid said no. Open Unraid > Main and press Start."
    fi
  else
    tell warning "Tower's drives are stopped" "After the crash the drives didn't start by themselves. Open Unraid > Main (http://$(hostname -i 2>/dev/null | awk '{print $1}'):8080/Main) and press Start."
  fi
}

# --------------------------------------------------------------------------- freeze timer

wd_running() { [ -f "$PIDF" ] && kill -0 "$(cat "$PIDF")" 2>/dev/null; }

watchdog_check() {
  if [ "$(setting watchdog on)" = on ]; then
    wd_running && { echo running >"$TMPD/wd"; return; }
    [ -c /dev/watchdog ] || modprobe iTCO_wdt heartbeat=180 nowayout=0 >>"$TMPD/wd.err" 2>&1
    sleep 1
    # A timer shorter than a minute could restart Tower during a slow moment, so it stays off then
    if [ -c /dev/watchdog ] && [ "$(cat /sys/class/watchdog/watchdog0/timeout 2>/dev/null || echo 0)" -ge 60 ]; then
      setsid nohup /bin/bash "$SELF" pinger >/dev/null 2>&1 </dev/null &
      sleep 1
      if wd_running; then echo running >"$TMPD/wd"; note "freeze timer on"; else echo unavailable >"$TMPD/wd"; fi
    else
      echo unavailable >"$TMPD/wd"
    fi
  else
    if wd_running; then kill -TERM "$(cat "$PIDF")" 2>/dev/null; note "freeze timer off"; fi
    echo off >"$TMPD/wd"
  fi
}

pinger() {
  wd_running && exit 0
  echo $$ >"$PIDF"
  echo -1000 >/proc/$$/oom_score_adj 2>/dev/null
  exec 3>/dev/watchdog || { rm -f "$PIDF"; exit 1; }
  # "V" before closing tells the board's timer to stop, so a clean stop never turns into a restart
  trap 'printf V >&3; exec 3>&-; rm -f "$PIDF"; exit 0' TERM INT HUP
  while :; do
    printf . >&3
    sleep 20 &
    wait $!
  done
}

# --------------------------------------------------------------------------- black box

sample() { # sample <first of the minute? 1|0>
  local now dir f cpu idle total pcpu=null pmem load ct at af ap=null ag=null nt bt ups=null lv=null ul=null ff mv par apps=null
  local h e pe pt a c top
  now=$(date +%s)
  read -r _ a b c d e g h2 _ </proc/stat
  total=$((a + b + c + d + e + g + h2)); idle=$((d + e))
  if read -r pt pi <"$TMPD/cpu" 2>/dev/null && [ "$total" -gt "${pt:-0}" ]; then
    pcpu=$((100 - 100 * (idle - pi) / (total - pt)))
  fi
  echo "$total $idle" >"$TMPD/cpu"
  pmem=$(awk '/^MemTotal:/{t=$2} /^MemAvailable:/{a=$2} END{if(t) printf "%d", 100-100*a/t}' /proc/meminfo)
  load=$(cut -d' ' -f1 /proc/loadavg)
  h=$(hwmon coretemp); ct=$(milli "$h/temp1_input")
  h=$(hwmon i915); at=$(milli "$h/temp1_input"); af=$(cat "$h/fan1_input" 2>/dev/null)
  if [ -r "$h/energy1_input" ]; then
    e=$(cat "$h/energy1_input")
    if read -r pe ptime <"$TMPD/energy" 2>/dev/null && [ "$now" -gt "${ptime:-0}" ] && [ "$e" -ge "${pe:-0}" ]; then
      ap=$(((e - pe) / (now - ptime) / 1000000))
    fi
    echo "$e $now" >"$TMPD/energy"
  fi
  for c in /sys/class/drm/card[0-9]*; do
    [ -d "$c/device/hwmon" ] || continue
    a=$(cat "$c/gt_act_freq_mhz" 2>/dev/null || cat "$c/gt/gt0/rps_act_freq_mhz" 2>/dev/null)
    top=$(cat "$c/gt_RP0_freq_mhz" 2>/dev/null || cat "$c/gt/gt0/rps_RP0_freq_mhz" 2>/dev/null)
    [ -n "$a" ] && [ "${top:-0}" -gt 0 ] && ag=$((100 * a / top))
    break
  done
  nt=$(for h in /sys/class/hwmon/hwmon*; do [ "$(cat "$h/name" 2>/dev/null)" = nvme ] && milli "$h/temp1_input" && echo; done | sort -n | tail -1)
  h=$(hwmon nct6798); [ -n "$h" ] || h=$(for x in /sys/class/hwmon/hwmon*; do case "$(cat "$x/name" 2>/dev/null)" in nct*|it87*) echo "$x"; break ;; esac; done)
  bt=$(milli "$h/temp1_input")
  if command -v apcaccess >/dev/null 2>&1; then
    read -r ups lv ul <<<"$(apcaccess status 2>/dev/null | awk -F': *' '/^STATUS/{s=$2} /^LINEV/{v=$2+0} /^LOADPCT/{l=$2+0} END{gsub(/ +$/,"",s); gsub(/ /,"_",s); print (s==""?"-":s), v, l}')"
    [ "$ups" = "-" ] && { ups=""; lv=null; ul=null; }
  fi
  ff=$(pgrep -c ffmpeg 2>/dev/null); ff=${ff:-0}
  mv=0; pgrep -f "sbin/mover" >/dev/null 2>&1 && mv=1
  par=0; [ "$(var mdResyncPos)" != "" ] && [ "$(var mdResyncPos)" != 0 ] && par=1
  if [ "$1" = 1 ] && [ -S /var/run/docker.sock ]; then
    apps=$(timeout 8 docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' 2>/dev/null | sed 's/%$//' | sort -k2 -rn | head -3 | awk '{printf "%s%s %d%%", (NR>1?", ":""), $1, $2}')
    apps=$(jstr "$apps")
  fi
  dir=$(bb_dir); [ -n "$dir" ] || dir=$TMPD
  f="$dir/blackbox-$(date +%F).jsonl"
  printf '{"t":%s,"cpu":%s,"mem":%s,"load":%s,"ct":%s,"at":%s,"af":%s,"ap":%s,"ag":%s,"nt":%s,"bt":%s,"ups":%s,"lv":%s,"ul":%s,"ff":%s,"mv":%s,"par":%s,"apps":%s}\n' \
    "$now" "$(num "$pcpu")" "$(num "$pmem")" "$(num "$load")" "$(num "$ct")" "$(num "$at")" "$(num "$af")" "$(num "$ap")" "$(num "$ag")" "$(num "$nt")" "$(num "$bt")" \
    "$([ -n "$ups" ] && [ "$ups" != null ] && jstr "$ups" || echo null)" "$(num "$lv")" "$(num "$ul")" "$ff" "$mv" "$par" "$apps" >>"$f"
  sync "$f" 2>/dev/null
  # Too hot? One message an hour at most
  if [ "$(setting hotnote on)" = on ]; then
    local hot=""
    [ "${ct:-0}" -ge 85 ] 2>/dev/null && hot="the processor is at ${ct}°C"
    [ "${at:-0}" -ge 90 ] 2>/dev/null && hot="the Arc is at ${at}°C"
    [ "${nt:-0}" -ge 75 ] 2>/dev/null && hot="an NVMe drive is at ${nt}°C"
    if [ -n "$hot" ] && [ -z "$(find "$TMPD/hot" -mmin -60 2>/dev/null)" ]; then
      touch "$TMPD/hot"
      tell warning "Tower is running hot" "Right now $hot. Check the fans and that nothing blocks the air."
    fi
  fi
}

# Which app ran out of memory, and whether it hit its own limit or Tower ran out. One entry per app and stopped
# program: "app|program|times|own|cap MB|last kill (epoch)" joined with ";". Worked out again only when a new kill
# shows up in the log, so the minute job doesn't ask Docker every minute.
oom_apps() {
  local log=$1 n ids
  local -A caps
  n=$(grep -c 'oom-kill:constraint' "$log" 2>/dev/null)
  [ "${n:-0}" -gt 0 ] || { rm -f "$TMPD/oom"; return; }
  if [ "$(sed -n 1p "$TMPD/oom" 2>/dev/null)" != "$n" ]; then
    ids=$(timeout 8 docker ps -a --no-trunc --format '{{.ID}} {{.Names}}' 2>/dev/null)
    {
      echo "$n"
      grep -h 'oom-kill:constraint' "$log" 2>/dev/null | while read -r ln; do
        local c t id name when cap=0
        c=$(echo "$ln" | grep -oE 'constraint=[A-Z_]+' | cut -d= -f2)
        t=$(echo "$ln" | grep -oE ',task=[^,]+' | cut -d= -f2)
        id=$(echo "$ln" | grep -oE 'task_memcg=[^,]*' | grep -oE '[0-9a-f]{64}' | head -1)
        name=""; [ -n "$id" ] && name=$(echo "$ids" | awk -v i="$id" '$1==i{print $2; exit}')
        if [ -n "$name" ] && [ -z "${caps[$name]}" ]; then
          caps[$name]=$(( $(timeout 5 docker inspect -f '{{.HostConfig.Memory}}' "$name" 2>/dev/null || echo 0) / 1048576 ))
        fi
        [ -n "$name" ] && cap=${caps[$name]:-0}
        when=$(date -d "$(echo "$ln" | cut -c1-15)" +%s 2>/dev/null || echo 0)
        echo "${name:-Tower}|${t:-?}|$([ "$c" = CONSTRAINT_MEMCG ] && echo own || echo all)|$cap|$when"
      done | awk -F'|' '{k=$1"|"$2"|"$3"|"$4; n[k]++; if($5>l[k]) l[k]=$5} END{for(k in n) print n[k]"|"k"|"l[k]}' | sort -t'|' -k1,1 -rn |
        awk -F'|' '{printf "%s%s|%s|%s|%s|%s|%s", (NR>1?";":""), $2, $3, $1, $4, $5, $6} END{print ""}'
    } >"$TMPD/oom.tmp" && mv "$TMPD/oom.tmp" "$TMPD/oom"
  fi
  sed -n 2p "$TMPD/oom"
}

# Everything Glowfin's Health page needs that only Tower itself can see, once a minute
status() {
  local dir errors dimms board bios cpu link cap ups_model hc=0 oom_apps
  dir=$(bb_dir); [ -n "$dir" ] || return
  if [ ! -f "$TMPD/hw" ]; then
    {
      echo "board=$(dmidecode -s baseboard-product-name 2>/dev/null | head -1)"
      echo "bios=$(dmidecode -s bios-version 2>/dev/null | head -1)"
      echo "cpu=$(grep -m1 'model name' /proc/cpuinfo | sed 's/.*: //; s/  */ /g')"
      echo "dimms=$(dmidecode -t 17 2>/dev/null | awk -F': ' '/^\tSize:/{s=$2} /^\tPart Number:/{p=$2; gsub(/ +$/,"",p)} /^\tConfigured Memory Speed:/{c=$2} /^$/{if(s!="" && s!~/No Module/){printf "%s%s|%s|%s", (n++?";":""), s, c, p} s="";p="";c=""}')"
      echo "ups_model=$(apcaccess status 2>/dev/null | sed -n 's/^MODEL *: *//p' | sed 's/ *$//')"
    } >"$TMPD/hw"
  fi
  board=$(sed -n 's/^board=//p' "$TMPD/hw"); bios=$(sed -n 's/^bios=//p' "$TMPD/hw"); cpu=$(sed -n 's/^cpu=//p' "$TMPD/hw")
  dimms=$(sed -n 's/^dimms=//p' "$TMPD/hw"); ups_model=$(sed -n 's/^ups_model=//p' "$TMPD/hw")
  link=$(lspci -vv -s 00:01.0 2>/dev/null | sed -n 's/.*LnkSta:[^S]*Speed \([^,]*\), Width \([^ ,]*\).*/\1 \2/p' | head -1)
  cap=$(lspci -vv -s 00:01.0 2>/dev/null | sed -n 's/.*LnkCap:.*Speed \([^,]*\), Width \([^ ,]*\).*/\1 \2/p' | head -1)
  [ -f /boot/config/plugins/user.scripts/scripts/healthcheck/script ] && hc=1
  local log=/var/log/syslog
  errors=$(printf '"mce":%s,"aer_arc":%s,"aer_other":%s,"gpu":%s,"oom":%s,"disk":%s' \
    "$(grep -ciE 'mce: \[Hardware Error\]|Machine check events logged|Machine Check Exception' "$log" 2>/dev/null)" \
    "$(grep -E 'AER: (Corrected|Uncorrected|Multiple)|PCIe Bus Error' "$log" 2>/dev/null | grep -cE '0000:00:01\.0|0000:0[1-3]:00')" \
    "$(grep -E 'AER: (Corrected|Uncorrected|Multiple)|PCIe Bus Error' "$log" 2>/dev/null | grep -cvE '0000:00:01\.0|0000:0[1-3]:00')" \
    "$(grep -ciE 'i915 .*(GPU HANG|GPU reset|Resetting|\*ERROR\*)' "$log" 2>/dev/null)" \
    "$(grep -c 'invoked oom-killer' "$log" 2>/dev/null)" \
    "$(grep -ciE 'I/O error|ata[0-9.]+: (exception|hard resetting)|nvme.*(timeout|resetting)' "$log" 2>/dev/null)")
  oom_apps=$(oom_apps "$log")
  printf '{"t":%s,"tz":"%s","boot":%s,"array":%s,"bert":%s,"errors":{%s},"oom_apps":%s,"board":%s,"bios":%s,"cpu":%s,"dimms":%s,"link":%s,"link_cap":%s,"ups_model":%s,"wd":%s,"hc":%s,"crashed":%s,"drives_started":%s,"settings":{"watchdog":%s,"autostart":%s,"crashnote":%s,"hotnote":%s}}\n' \
    "$(date +%s)" "$(date +%z)" "$(booted_at)" "$(jstr "$(var mdState)")" "$(dmesg 2>/dev/null | grep -q 'BERT: \[Hardware Error\]' && echo 1 || echo 0)" "$errors" "$(jstr "$oom_apps")" \
    "$(jstr "$board")" "$(jstr "$bios")" "$(jstr "$cpu")" "$(jstr "$dimms")" "$(jstr "$link")" "$(jstr "$cap")" "$(jstr "$ups_model")" \
    "$(jstr "$(cat "$TMPD/wd" 2>/dev/null)")" "$hc" "$([ -f "$TMPD/crashed" ] && echo 1 || echo 0)" "$(num "$(cat "$TMPD/drives-started" 2>/dev/null)")" \
    "$(jstr "$(setting watchdog on)")" "$(jstr "$(setting autostart on)")" "$(jstr "$(setting crashnote on)")" "$(jstr "$(setting hotnote on)")" >"$dir/status.tmp" \
    && mv "$dir/status.tmp" "$dir/status.json"
  # A copy of the crash list where the helper can read it (the USB stick's own copy is the real one)
  cp "$FLASH/boots.jsonl" "$dir/boots.jsonl" 2>/dev/null
  # Seven days of black box is plenty
  [ "$(date +%M)" = 05 ] && find "$dir" -maxdepth 1 -name 'blackbox-*.jsonl' -mtime +7 -delete 2>/dev/null
  # Settings Glowfin asked for (the helper can't write to the USB stick, so it leaves them here)
  if [ -f "$dir/settings.want" ]; then
    grep -E '^(watchdog|autostart|crashnote|hotnote)=(on|off)$' "$dir/settings.want" >"$FLASH/settings.cfg.tmp" && mv "$FLASH/settings.cfg.tmp" "$FLASH/settings.cfg"
    rm -f "$dir/settings.want"
    watchdog_check
  fi
}

run() {
  exec 9>/tmp/benflix-health.lock
  flock -n 9 || exit 0
  local start i=0
  start=$(date +%s)
  boot_check
  watchdog_check
  drives_after_crash
  while :; do
    sample $([ "$i" = 0 ] && echo 1 || echo 0)
    [ "$i" = 0 ] && status
    i=$((i + 1))
    [ $(($(date +%s) - start)) -ge 48 ] && break
    sleep 10
  done
}

stop() {
  echo "$(boot_id) $(date +%s)" >"$FLASH/clean-stop"
  sync "$FLASH/clean-stop" 2>/dev/null
  if wd_running; then kill -TERM "$(cat "$PIDF")" 2>/dev/null; sleep 1; fi
}

install() {
  mkdir -p /boot/config/benflix "$FLASH"
  [ "$0" -ef "$SELF" ] || cp "$0" "$SELF"
  local cron=/boot/config/plugins/dynamix/benflix-health.cron line hook
  line="* * * * * /bin/bash $SELF run >/dev/null 2>&1"
  if command -v update_cron >/dev/null 2>&1 && mkdir -p "$(dirname "$cron")"; then
    printf '# Glowfin: Tower health black box, crash check and freeze timer, every minute\n%s\n' "$line" >"$cron"
    update_cron
  else
    (crontab -l 2>/dev/null | grep -v benflix/health.sh; echo "$line") | crontab -
  fi
  # A clean shutdown says so (and switches the freeze timer off) before Tower stops
  hook="[ -f $SELF ] && /bin/bash $SELF stop # Glowfin Tower health"
  [ -f /boot/config/stop ] || printf '#!/bin/bash\n' >/boot/config/stop
  grep -qF "$SELF stop" /boot/config/stop || echo "$hook" >>/boot/config/stop
  mkdir -p "$BB" 2>/dev/null
  echo "  ok  Tower health: black box every 10 seconds, crash check, freeze timer ($(setting watchdog on))"
}

case "$1" in
  run) run ;;
  stop) stop ;;
  pinger) pinger ;;
  install) install ;;
  *) echo "Use: $0 run|stop|pinger|install" ;;
esac
