/* Benflix Easter: the Who's watching spring room and the Easter Sunday egg hunt (approved 2026-11-17,
 * thread "Design an Easter theme for Benflix"; sheets easter-1-look.html and easter-2-whos-watching.html).
 *
 * A soft sage-green room in early morning light with pastel paper bunting. Down in the bottom left, a small
 * wooden table with an egg tree (pussy willow in a green jug, eleven painted eggs swaying), a basket of eggs,
 * a gold-foil chocolate bunny and a pot of tulips, and the pet rabbit sitting on the floor (its nose twitches,
 * its ears turn, it blinks and does a little hop now and then). Through the window: a spring garden with a
 * blossom tree and a picket fence, petals drifting, and people walking past (a duck with three ducklings, a kid
 * in a yellow raincoat with a basket, a grown-up with tulips, and a wild rabbit hopping).
 *
 * Easter Sunday: someone has bitten the chocolate bunny's ear, eggs are hidden in the grass outside, a golden
 * egg sits on the table, and one egg is hidden on each of the six door pages. Find all six and the golden egg
 * cracks open on Who's watching ("You found all six!").
 *
 * The season: Monday to Easter Sunday, worked out for each year. It never starts before 18 March (St Patrick's
 * comes first) and stops by 21 April (Earth Day keeps 22 April). The app has the same dates (EasterSeason.kt).
 *
 * Like feast.js, the same drawing is used live on the web and by tools/easter/record.js, which records it as
 * looping videos for the TV and phone app. Everything moves as a function of t (seconds) and repeats every
 * LOOP seconds, so a video of one loop joins up with no jump. tools/easter/pictures.js draws the door charms
 * and small touches from the kit below as pictures.
 */
(function (G) {
  "use strict";
  const LOOP = 36;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;

  // ---------- dates ----------
  /** Easter Sunday in year y (the Western date, worked out the usual way) */
  function easterSunday(y) {
    const a = y % 19, b = Math.floor(y / 100), c = y % 100, d = Math.floor(b / 4), e = b % 4;
    const f = Math.floor((b + 8) / 25), g = Math.floor((b - f + 1) / 3), h = (19 * a + b - d - g + 15) % 30;
    const i = Math.floor(c / 4), k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451);
    const month = Math.floor((h + l - 7 * m + 114) / 31), day = ((h + l - 7 * m + 114) % 31) + 1;
    return new Date(y, month - 1, day);
  }
  const dayOf = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
  /** The season's first and last days in year y */
  function seasonOf(y) {
    const sun = easterSunday(y);
    let start = new Date(y, sun.getMonth(), sun.getDate() - 6);
    const first = new Date(y, 2, 18); if (start < first) start = first;
    const last = new Date(y, 3, 21);
    return { start, end: sun > last ? last : sun, sun };
  }
  /** Monday to Easter Sunday (never before 18 March, never after 21 April) */
  const season = (d) => { const x = dayOf(d), s = seasonOf(x.getFullYear()); return x >= s.start && x <= s.end; };
  const isSunday = (d) => +dayOf(d) === +easterSunday(d.getFullYear());
  /** "week" or "day" (Easter Sunday) */
  const variant = (d) => (isSunday(d) ? "day" : "week");
  const greeting = () => "Happy Easter";
  /** The line under the greeting: "5 days to Easter", or on the day, how the egg hunt is going */
  function countdown(d) {
    if (isSunday(d)) {
      const n = hunt.found(d).length;
      if (n >= 6) return "The golden egg is open. Happy Easter!";
      return n ? `${n} of 6 eggs found` : "Six eggs are hidden around Benflix today";
    }
    const n = Math.round((easterSunday(d.getFullYear()) - dayOf(d)) / 864e5);
    return n === 1 ? "1 day to Easter" : n > 1 ? `${n} days to Easter` : "";
  }

  // ---------- the Easter Sunday egg hunt (this browser) ----------
  const DOOR_KEYS = ["movies", "shows", "live", "games", "music", "books"];
  const DOOR_NAMES = { movies: "Movies", shows: "Shows", live: "Live TV", games: "Games", music: "Music", books: "Books" };
  const hunt = {
    key: (d) => "benflix.eggs." + d.getFullYear(),
    found(d) { try { return JSON.parse(localStorage.getItem(this.key(d)) || "[]").filter((k) => DOOR_KEYS.includes(k)); } catch (e) { return []; } },
    find(door, d) { const f = this.found(d); if (!f.includes(door)) f.push(door); try { localStorage.setItem(this.key(d), JSON.stringify(f)); } catch (e) {} return f.length; },
    all(d) { return this.found(d).length >= 6; },
    /** the golden egg has already cracked open once on Who's watching (after that it just stays open) */
    cracked(d) { try { return localStorage.getItem(this.key(d) + ".open") === "1"; } catch (e) { return false; } },
    setCracked(d) { try { localStorage.setItem(this.key(d) + ".open", "1"); } catch (e) {} },
  };

  // ---------- drawing kit (from the approved design sheets) ----------
let UID=0;const u=p=>p+(UID++)+'_';
const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
const f1=v=>(+v).toFixed(1);
const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
const GDEF=`<defs><filter id="Gts" x="-10%" y="-40%" width="120%" height="180%"><feDropShadow dx="0" dy="2" stdDeviation="2.5" flood-color="#000" flood-opacity=".55"/></filter></defs>`;
const T=(x,y,s,txt,o={})=>`<text x="${x}" y="${y}" font-family="${o.font||'Inter,sans-serif'}" font-weight="${o.w||600}" font-size="${s}" fill="${o.fill||'#F5F0EA'}" ${o.anchor?`text-anchor="${o.anchor}"`:''} ${o.sh?'filter="url(#Gts)"':''} ${o.op?`opacity="${o.op}"`:''}>${txt}</text>`;
const DW=240,DH=220,DR=20;
function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const sh=(id,s)=>`<filter id="${id}" x="-40%" y="-40%" width="180%" height="180%"><feDropShadow dx="0" dy="${f1(s*.6)}" stdDeviation="${s}" flood-color="#000" flood-opacity=".45"/></filter>`;
function pin(cx,cy,r,c='#F4F1EA'){const p=u('pn');return `<defs><radialGradient id="${p}" cx=".35" cy=".3" r=".85"><stop offset="0" stop-color="#fff"/><stop offset=".55" stop-color="${c}"/><stop offset="1" stop-color="${dk(c,.45)}"/></radialGradient></defs><circle cx="${cx}" cy="${f1(cy+r*.35)}" r="${r}" fill="#000" opacity=".3"/><circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#${p})" stroke="${dk(c,.55)}" stroke-width="1"/><circle cx="${f1(cx-r*.35)}" cy="${f1(cy-r*.35)}" r="${f1(r*.3)}" fill="#fff" opacity=".9"/>`}
/* satin ribbon hanging straight down from the pin, with a small bow at the pin */
function ribbon(cx,y1,y2,c){const d=dk(c,.25);return `<path d="M${cx} ${y1}L${cx} ${y2}" stroke="${d}" stroke-width="3.4"/><path d="M${cx} ${y1}L${cx} ${y2}" stroke="${lt(c,.3)}" stroke-width="1.2" opacity=".8"/>`}
function bow(cx,cy,s,c){const d=dk(c,.3);return `<path d="M${cx} ${cy}C${f1(cx-s*1.3)} ${f1(cy-s*.9)} ${f1(cx-s*1.5)} ${f1(cy+s*.8)} ${cx} ${f1(cy+s*.15)}Z" fill="${c}" stroke="${d}" stroke-width="1.2"/><path d="M${cx} ${cy}C${f1(cx+s*1.3)} ${f1(cy-s*.9)} ${f1(cx+s*1.5)} ${f1(cy+s*.8)} ${cx} ${f1(cy+s*.15)}Z" fill="${c}" stroke="${d}" stroke-width="1.2"/>
 <path d="M${f1(cx-2)} ${f1(cy+s*.2)}L${f1(cx-s*.7)} ${f1(cy+s*1.3)}L${f1(cx-s*.25)} ${f1(cy+s*1.1)}Z M${f1(cx+2)} ${f1(cy+s*.2)}L${f1(cx+s*.6)} ${f1(cy+s*1.35)}L${f1(cx+s*.15)} ${f1(cy+s*1.1)}Z" fill="${c}" stroke="${d}" stroke-width="1.1"/>
 <ellipse cx="${cx}" cy="${f1(cy+s*.08)}" rx="${f1(s*.28)}" ry="${f1(s*.32)}" fill="${lt(c,.1)}" stroke="${d}" stroke-width="1.1"/><path d="M${f1(cx-s*1.05)} ${f1(cy-s*.1)}Q${f1(cx-s*.6)} ${f1(cy-s*.55)} ${f1(cx-s*.2)} ${f1(cy-s*.05)}" stroke="#fff" stroke-width="1.4" fill="none" opacity=".55"/>`}
const eggPath=(cx,cy,rx,ry)=>`M${f1(cx)} ${f1(cy-ry)}C${f1(cx+rx*.78)} ${f1(cy-ry)} ${f1(cx+rx)} ${f1(cy-ry*.15)} ${f1(cx+rx)} ${f1(cy+ry*.22)}C${f1(cx+rx)} ${f1(cy+ry*.74)} ${f1(cx+rx*.56)} ${f1(cy+ry)} ${f1(cx)} ${f1(cy+ry)}C${f1(cx-rx*.56)} ${f1(cy+ry)} ${f1(cx-rx)} ${f1(cy+ry*.74)} ${f1(cx-rx)} ${f1(cy+ry*.22)}C${f1(cx-rx)} ${f1(cy-ry*.15)} ${f1(cx-rx*.78)} ${f1(cy-ry)} ${f1(cx)} ${f1(cy-ry)}Z`;
/* a hand-painted egg. pat = zig | dots | stripes | flowers | waves | checks */
function egg(cx,cy,rx,ry,base,acc,pat,o={}){const p=u('eg');const P=eggPath(cx,cy,rx,ry);let d='';const W='#FFFDF6';
 const band=(y,h,c)=>`<rect x="${f1(cx-rx-2)}" y="${f1(y)}" width="${f1(rx*2+4)}" height="${f1(h)}" fill="${c}"/>`;
 if(pat==='zig'){const z=(y,a,c,w)=>{let s=`M${f1(cx-rx-4)} ${f1(y)}`;for(let x=cx-rx-4,k=0;x<=cx+rx+8;x+=rx*.32,k++)s+=`L${f1(x)} ${f1(y+(k%2?-a:a))}`;return `<path d="${s}" stroke="${c}" stroke-width="${w}" fill="none" stroke-linejoin="round"/>`};
  d=band(cy-ry*.08,ry*.42,acc)+z(cy-ry*.08,ry*.09,W,ry*.1)+z(cy+ry*.34,ry*.09,W,ry*.1)+z(cy-ry*.5,ry*.07,acc,ry*.07)+z(cy+ry*.72,ry*.07,acc,ry*.07)}
 if(pat==='dots'){const R=rnd(5);for(let j=-3;j<=3;j++)for(let i=-3;i<=3;i++){const x=cx+i*rx*.46+(j%2?rx*.23:0),y=cy+j*ry*.3;d+=`<circle cx="${f1(x)}" cy="${f1(y)}" r="${f1(rx*.13)}" fill="${(i+j)%2?W:acc}"/>`}}
 if(pat==='stripes'){[-.62,-.3,.02,.34,.66].forEach((t,k)=>d+=band(cy+ry*t,ry*.14,k%2?W:acc))}
 if(pat==='flowers'){d=band(cy-ry*.02,ry*.06,W)+band(cy+ry*.5,ry*.06,W);[[-.5,-.45],[.45,-.35],[0,-.7],[-.62,.22],[.05,.24],[.62,.22],[-.3,.75],[.32,.78]].forEach(([a,b])=>{const x=cx+a*rx,y=cy+b*ry,r=rx*.13;for(let k=0;k<5;k++){const t=k/5*Math.PI*2;d+=`<circle cx="${f1(x+Math.cos(t)*r)}" cy="${f1(y+Math.sin(t)*r)}" r="${f1(r*.78)}" fill="${W}"/>`}d+=`<circle cx="${f1(x)}" cy="${f1(y)}" r="${f1(r*.62)}" fill="${acc}"/>`})}
 if(pat==='waves'){[-.55,-.15,.25,.65].forEach((t,k)=>{const y=cy+ry*t;let s=`M${f1(cx-rx-4)} ${f1(y)}`;for(let x=cx-rx-4;x<cx+rx+4;x+=rx*.5)s+=`q${f1(rx*.125)} ${f1(-ry*.12)} ${f1(rx*.25)} 0t${f1(rx*.25)} 0`;d+=`<path d="${s}" stroke="${k%2?W:acc}" stroke-width="${f1(ry*.1)}" fill="none" stroke-linecap="round"/>`})}
 if(pat==='checks'){const n=6;for(let j=0;j<n*1.4;j++)for(let i=0;i<n;i++)if((i+j)%2)d+=`<rect x="${f1(cx-rx+i*rx*2/n)}" y="${f1(cy-ry+j*ry*2/(n*1.4))}" width="${f1(rx*2/n)}" height="${f1(ry*2/(n*1.4))}" fill="${acc}" opacity=".9"/>`;d+=band(cy-ry*.04,ry*.1,W)}
 return `<defs><clipPath id="${p}c"><path d="${P}"/></clipPath><radialGradient id="${p}s" cx=".36" cy=".28" r=".9"><stop offset="0" stop-color="#fff" stop-opacity=".45"/><stop offset=".45" stop-color="#fff" stop-opacity="0"/><stop offset=".85" stop-color="#000" stop-opacity=".22"/><stop offset="1" stop-color="#000" stop-opacity=".38"/></radialGradient>${o.nosh?'':sh(p+'f',3.5)}</defs>
 <g ${o.nosh?'':`filter="url(#${p}f)"`}><path d="${P}" fill="${base}"/><g clip-path="url(#${p}c)">${d}</g><path d="${P}" fill="url(#${p}s)"/><path d="${P}" fill="none" stroke="${dk(base,.4)}" stroke-width="1"/></g>
 <ellipse cx="${f1(cx-rx*.38)}" cy="${f1(cy-ry*.5)}" rx="${f1(rx*.16)}" ry="${f1(ry*.24)}" fill="#fff" opacity=".7" transform="rotate(22 ${f1(cx-rx*.38)} ${f1(cy-ry*.5)})"/>`}

/* idea 1: Easter tree charms, a different thing on every door, each hung on a ribbon from a pearl pin */
function eggCharm(){const cx=186,cy=84;return ribbon(cx,8,cy-48,'#F28DB2')+egg(cx,cy,34,46,'#FFB8D2','#7B5BD6','zig')+bow(cx,cy-48,11,'#F28DB2')+pin(cx,8,6)}
function bunnyHead(cx,cy,r){const p=u('bn');const F='#F7F3EC',E='#E9E1D4',PK='#F4A3BD';const ear=(a)=>`<g transform="rotate(${a} ${cx+(a<0?-r*.38:r*.38)} ${cy-r*.6})"><ellipse cx="${f1(cx+(a<0?-r*.38:r*.38))}" cy="${f1(cy-r*1.35)}" rx="${f1(r*.34)}" ry="${f1(r*.9)}" fill="${F}" stroke="${E}" stroke-width="1.5"/><ellipse cx="${f1(cx+(a<0?-r*.38:r*.38))}" cy="${f1(cy-r*1.3)}" rx="${f1(r*.17)}" ry="${f1(r*.66)}" fill="${PK}"/><ellipse cx="${f1(cx+(a<0?-r*.38:r*.38))}" cy="${f1(cy-r*1.35)}" rx="${f1(r*.29)}" ry="${f1(r*.85)}" fill="none" stroke="#C9BBA6" stroke-width="1.3" stroke-dasharray="3 3"/></g>`;
 return `<defs><radialGradient id="${p}g" cx=".4" cy=".35" r=".75"><stop offset="0" stop-color="#fff"/><stop offset="1" stop-color="${E}"/></radialGradient>${sh(p+'s',3.5)}</defs><g filter="url(#${p}s)">${ear(-14)}${ear(12)}
 <ellipse cx="${cx}" cy="${cy}" rx="${f1(r*1.02)}" ry="${f1(r*.9)}" fill="url(#${p}g)" stroke="${E}" stroke-width="1.5"/></g>
 <ellipse cx="${cx}" cy="${cy}" rx="${f1(r*.94)}" ry="${f1(r*.82)}" fill="none" stroke="#C9BBA6" stroke-width="1.3" stroke-dasharray="3 3"/>
 <circle cx="${f1(cx-r*.36)}" cy="${f1(cy-r*.08)}" r="${f1(r*.11)}" fill="#2A211C"/><circle cx="${f1(cx+r*.36)}" cy="${f1(cy-r*.08)}" r="${f1(r*.11)}" fill="#2A211C"/><circle cx="${f1(cx-r*.33)}" cy="${f1(cy-r*.12)}" r="${f1(r*.035)}" fill="#fff"/><circle cx="${f1(cx+r*.39)}" cy="${f1(cy-r*.12)}" r="${f1(r*.035)}" fill="#fff"/>
 <ellipse cx="${f1(cx-r*.58)}" cy="${f1(cy+r*.22)}" rx="${f1(r*.17)}" ry="${f1(r*.11)}" fill="${PK}" opacity=".7"/><ellipse cx="${f1(cx+r*.58)}" cy="${f1(cy+r*.22)}" rx="${f1(r*.17)}" ry="${f1(r*.11)}" fill="${PK}" opacity=".7"/>
 <path d="M${f1(cx-r*.11)} ${f1(cy+r*.12)}L${f1(cx+r*.11)} ${f1(cy+r*.12)}L${cx} ${f1(cy+r*.26)}Z" fill="#E07A9A"/><path d="M${cx} ${f1(cy+r*.26)}v${f1(r*.12)} M${cx} ${f1(cy+r*.38)}q${f1(-r*.12)} ${f1(r*.1)} ${f1(-r*.22)} 0 M${cx} ${f1(cy+r*.38)}q${f1(r*.12)} ${f1(r*.1)} ${f1(r*.22)} 0" stroke="#8A6A5A" stroke-width="1.6" fill="none" stroke-linecap="round"/>
 <path d="M${f1(cx-r*.3)} ${f1(cy+r*.3)}l${f1(-r*.5)} ${f1(-r*.06)} M${f1(cx-r*.3)} ${f1(cy+r*.36)}l${f1(-r*.48)} ${f1(r*.1)} M${f1(cx+r*.3)} ${f1(cy+r*.3)}l${f1(r*.5)} ${f1(-r*.06)} M${f1(cx+r*.3)} ${f1(cy+r*.36)}l${f1(r*.48)} ${f1(r*.1)}" stroke="#B8A898" stroke-width="1"/>`}
function bunnyCharm(){const cx=186,cy=96,r=30;return ribbon(cx,8,cy-r*1.9,'#8FC6E8')+bunnyHead(cx,cy,r)+bow(cx,cy-r*1.95,9,'#8FC6E8')+pin(cx,8,6)}
function chick(cx,cy,r){const p=u('ck');return `<defs><radialGradient id="${p}" cx=".4" cy=".35" r=".8"><stop offset="0" stop-color="#FFF6A8"/><stop offset=".6" stop-color="#FFD83D"/><stop offset="1" stop-color="#E5A915"/></radialGradient></defs>
 <circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#${p})" stroke="#C88A0A" stroke-width="1"/>
 <path d="M${f1(cx-r*.15)} ${f1(cy-r*.95)}q${f1(-r*.1)} ${f1(-r*.4)} ${f1(r*.15)} ${f1(-r*.42)} M${f1(cx)} ${f1(cy-r*.98)}q${f1(r*.15)} ${f1(-r*.35)} ${f1(r*.35)} ${f1(-r*.2)}" stroke="#E5A915" stroke-width="2.4" fill="none" stroke-linecap="round"/>
 <circle cx="${f1(cx-r*.34)}" cy="${f1(cy-r*.15)}" r="${f1(r*.12)}" fill="#2A211C"/><circle cx="${f1(cx+r*.34)}" cy="${f1(cy-r*.15)}" r="${f1(r*.12)}" fill="#2A211C"/><circle cx="${f1(cx-r*.3)}" cy="${f1(cy-r*.2)}" r="${f1(r*.04)}" fill="#fff"/><circle cx="${f1(cx+r*.38)}" cy="${f1(cy-r*.2)}" r="${f1(r*.04)}" fill="#fff"/>
 <path d="M${f1(cx-r*.18)} ${f1(cy+r*.05)}L${f1(cx+r*.18)} ${f1(cy+r*.05)}L${cx} ${f1(cy+r*.34)}Z" fill="#F08A24" stroke="#B85E10" stroke-width="1"/>
 <ellipse cx="${f1(cx-r*.62)}" cy="${f1(cy+r*.2)}" rx="${f1(r*.16)}" ry="${f1(r*.1)}" fill="#F7A06A" opacity=".6"/><ellipse cx="${f1(cx+r*.62)}" cy="${f1(cy+r*.2)}" rx="${f1(r*.16)}" ry="${f1(r*.1)}" fill="#F7A06A" opacity=".6"/>`}
function shell(cx,cy,rx,ry,top){/* half an eggshell with a jagged crack edge */const p=u('sl');const n=8;let edge='';for(let k=0;k<=n;k++){const x=cx-rx+k*rx*2/n,y=cy+(k%2?-ry*.16:ry*.12);edge+=(k?'L':'M')+f1(x)+' '+f1(y)}
 const body=top?`${edge}C${f1(cx+rx)} ${f1(cy-ry*.6)} ${f1(cx+rx*.6)} ${f1(cy-ry)} ${cx} ${f1(cy-ry)}C${f1(cx-rx*.6)} ${f1(cy-ry)} ${f1(cx-rx)} ${f1(cy-ry*.6)} ${f1(cx-rx)} ${f1(cy+ry*.12)}Z`:`${edge}C${f1(cx+rx*1.02)} ${f1(cy+ry*.7)} ${f1(cx+rx*.6)} ${f1(cy+ry*1.05)} ${cx} ${f1(cy+ry*1.05)}C${f1(cx-rx*.6)} ${f1(cy+ry*1.05)} ${f1(cx-rx*1.02)} ${f1(cy+ry*.7)} ${f1(cx-rx)} ${f1(cy+ry*.12)}Z`;
 return `<defs><radialGradient id="${p}" cx=".35" cy=".3" r=".9"><stop offset="0" stop-color="#FFFFFF"/><stop offset=".7" stop-color="#DDEFFB"/><stop offset="1" stop-color="#A9CBE3"/></radialGradient></defs><path d="${body}" fill="url(#${p})" stroke="#7FA6C2" stroke-width="1.1" stroke-linejoin="round"/>`}
function chickCharm(){const p=u('cc');const cx=186,cy=104;return `<defs>${sh(p,3.5)}</defs>`+ribbon(cx,8,cy-66,'#F6C64A')+`<g filter="url(#${p})">${chick(cx,cy-6,24)}${shell(cx,cy+14,34,30,false)}<g transform="rotate(-14 ${cx} ${cy-36})">${shell(cx,cy-36,22,20,true)}</g></g>`+bow(cx,cy-64,9,'#F6C64A')+pin(cx,8,6)}
function basket(cx,top,w){const p=u('bk');const h=w*.55,rimY=top+w*.42;let weave='';
 for(let j=0;j<5;j++){const y=rimY+4+j*h*.2;weave+=`<rect x="${f1(cx-w/2)}" y="${f1(y)}" width="${w}" height="${f1(h*.17)}" rx="${f1(h*.085)}" fill="${j%2?'#C8924E':'#B07A3C'}" stroke="#6E4520" stroke-width=".8"/><path d="M${f1(cx-w/2)} ${f1(y+h*.05)}H${f1(cx+w/2)}" stroke="#E2B272" stroke-width="1.3" opacity=".6"/>`;
  for(let i=0;i<9;i++)if((i+j)%2)weave+=`<rect x="${f1(cx-w/2+w*.06+i*w*.11-3.5)}" y="${f1(y-1.5)}" width="7" height="${f1(h*.2+1)}" rx="3" fill="#9A6630" stroke="#6E4520" stroke-width=".8"/>`}
 const B=`M${f1(cx-w/2)} ${f1(rimY)}L${f1(cx+w/2)} ${f1(rimY)}L${f1(cx+w*.38)} ${f1(rimY+h)}Q${cx} ${f1(rimY+h+8)} ${f1(cx-w*.38)} ${f1(rimY+h)}Z`;
 const grass=(()=>{let g='';const R=rnd(4);for(let k=0;k<16;k++){const x=cx-w*.46+k*w*.06;g+=`<path d="M${f1(x)} ${f1(rimY+2)}q${f1((R()-.5)*8)} ${f1(-8-R()*8)} ${f1((R()-.5)*14)} ${f1(-10-R()*10)}" stroke="${k%2?'#7CCB6A':'#5AAE52'}" stroke-width="2.4" fill="none" stroke-linecap="round"/>`}return g})();
 return `<defs><clipPath id="${p}"><path d="${B}"/></clipPath>${sh(p+'s',3.5)}</defs><g filter="url(#${p}s)">
 <path d="M${f1(cx-w*.44)} ${f1(rimY)}C${f1(cx-w*.44)} ${f1(top-6)} ${f1(cx+w*.44)} ${f1(top-6)} ${f1(cx+w*.44)} ${f1(rimY)}" stroke="#7A4E24" stroke-width="7" fill="none"/><path d="M${f1(cx-w*.44)} ${f1(rimY)}C${f1(cx-w*.44)} ${f1(top-6)} ${f1(cx+w*.44)} ${f1(top-6)} ${f1(cx+w*.44)} ${f1(rimY)}" stroke="#B8844A" stroke-width="3" fill="none" stroke-dasharray="6 4"/>
 ${egg(cx-w*.22,rimY-8,11,15,'#BFE6FF','#4F8FD6','dots',{nosh:true})}${egg(cx+w*.2,rimY-9,11,15,'#FFF1A6','#F08A24','stripes',{nosh:true})}${egg(cx,rimY-13,12,16,'#D9C7FF','#F28DB2','waves',{nosh:true})}${grass}
 <path d="${B}" fill="#8A5A2C"/><g clip-path="url(#${p})">${weave}</g><path d="${B}" fill="none" stroke="#5A3816" stroke-width="1.2"/>
 <rect x="${f1(cx-w/2-3)}" y="${f1(rimY-4)}" width="${f1(w+6)}" height="10" rx="5" fill="#B07A40" stroke="#5A3816" stroke-width="1"/><path d="M${f1(cx-w/2)} ${f1(rimY+1)}H${f1(cx+w/2)}" stroke="#7A4E24" stroke-width="2" stroke-dasharray="5 3"/></g>`}
function basketCharm(){const cx=186;return basket(cx,12,92)+pin(cx,10,6)}
function tulip(x,y,c,rot=0,s=1){const d=dk(c,.3),l=lt(c,.25);return `<g transform="rotate(${rot} ${x} ${y}) translate(${x} ${y}) scale(${s})"><path d="M-13 -2C-15 -16 -12 -26 -6 -30L0 -20L6 -30C12 -26 15 -16 13 -2C10 8 -10 8 -13 -2Z" fill="${c}" stroke="${d}" stroke-width="1.2"/><path d="M-6 -30C0 -34 4 -26 0 -20C-4 -26 0 -34 6 -30 C2 -18 2 -4 0 6C-2 -4 -2 -18 -6 -30Z" fill="${l}" stroke="${d}" stroke-width="1"/><path d="M-9 -6C-9 -16 -8 -22 -6 -26" stroke="#fff" stroke-width="2" opacity=".45" fill="none" stroke-linecap="round"/></g>`}
function stems(x,y,heads,leafC='#4FA052'){let s='';heads.forEach(([hx,hy])=>{s+=`<path d="M${x} ${y}Q${f1((x+hx)/2+(hx-x)*.2)} ${f1((y+hy)/2)} ${hx} ${hy}" stroke="#3E8A45" stroke-width="3.4" fill="none" stroke-linecap="round"/>`});return s}
function tulipCharm(){const p=u('tc');const bx=190,by=70;const H=[[166,34,'#FF6F91',-16],[192,22,'#FFD23F',0],[216,36,'#FFFFFF',16]];let s=`<defs>${sh(p,3.5)}</defs><g filter="url(#${p})">`;
 s+=`<path d="M${bx-4} ${by}C${bx-30} ${by+8} ${bx-34} ${by-30} ${bx-26} ${by-46}C${bx-14} ${by-26} ${bx-10} ${by-10} ${bx-4} ${by}Z" fill="#5BB35E" stroke="#2E7A36" stroke-width="1"/><path d="M${bx+4} ${by}C${bx+30} ${by+4} ${bx+36} ${by-26} ${bx+28} ${by-40}C${bx+18} ${by-22} ${bx+12} ${by-8} ${bx+4} ${by}Z" fill="#4FA052" stroke="#2E7A36" stroke-width="1"/>`;
 s+=stems(bx,by,H.map(h=>[h[0],h[1]+4]))+`<path d="M${bx-5} ${by}L${bx-8} ${by+52} M${bx} ${by}L${bx} ${by+56} M${bx+5} ${by}L${bx+9} ${by+50}" stroke="#3E8A45" stroke-width="3.4" stroke-linecap="round"/>`;
 H.forEach(([x,y,c,r])=>s+=tulip(x,y+4,c,r,1.15));s+=`</g>`;return `<g transform="translate(0 16)">`+s+bow(bx,by-2,11,'#FFF4D6')+pin(bx,by,5.5)+`</g>`}
function carrot(cx,ty,len,w){const p=u('cr');let rid='';for(let k=1;k<6;k++){const y=ty+len*k/6.5,hw=w/2*(1-k/6.5)*.8;rid+=`<path d="M${f1(cx-hw)} ${f1(y)}q${f1(hw*.6)} 3 ${f1(hw*.9)} 0" stroke="#C0540E" stroke-width="1.6" fill="none" stroke-linecap="round"/>`}
 return `<defs><linearGradient id="${p}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#FFA449"/><stop offset=".45" stop-color="#FF8A1F"/><stop offset="1" stop-color="#D9620C"/></linearGradient>${sh(p+'s',3.5)}</defs><g filter="url(#${p}s)">
 <path d="M${cx} ${ty+4}C${cx-14} ${ty-14} ${cx-26} ${ty-20} ${cx-30} ${ty-34}C${cx-16} ${ty-30} ${cx-6} ${ty-16} ${cx} ${ty+4}Z M${cx} ${ty+4}C${cx-2} ${ty-18} ${cx-6} ${ty-32} ${cx-2} ${ty-46}C${cx+8} ${ty-30} ${cx+6} ${ty-14} ${cx} ${ty+4}Z M${cx} ${ty+4}C${cx+12} ${ty-14} ${cx+24} ${ty-22} ${cx+30} ${ty-36}C${cx+16} ${ty-32} ${cx+6} ${ty-16} ${cx} ${ty+4}Z" fill="#5BB35E" stroke="#2E7A36" stroke-width="1"/>
 <path d="M${f1(cx-w/2)} ${ty+4}C${f1(cx-w/2)} ${ty-2} ${f1(cx+w/2)} ${ty-2} ${f1(cx+w/2)} ${ty+4}C${f1(cx+w*.42)} ${f1(ty+len*.5)} ${f1(cx+w*.1)} ${f1(ty+len*.9)} ${cx} ${f1(ty+len)}C${f1(cx-w*.1)} ${f1(ty+len*.9)} ${f1(cx-w*.42)} ${f1(ty+len*.5)} ${f1(cx-w/2)} ${ty+4}Z" fill="url(#${p})" stroke="#A8480A" stroke-width="1.1"/>${rid}
 <path d="M${f1(cx-w*.3)} ${ty+10}Q${f1(cx-w*.28)} ${f1(ty+len*.4)} ${f1(cx-w*.1)} ${f1(ty+len*.7)}" stroke="#fff" stroke-width="2.4" opacity=".4" fill="none" stroke-linecap="round"/></g>`}
function carrotCharm(){const cx=186,ty=60;return `<path d="M${cx} 8L${cx-2} ${ty-6} M${cx} 8L${cx+3} ${ty-6}" stroke="#C9A877" stroke-width="2.2"/><path d="M${cx-9} ${ty-4}q9 6 18 0" stroke="#C9A877" stroke-width="3" fill="none"/>`+carrot(cx,ty,72,34)+pin(cx,8,6)}
const CHARM={movies:eggCharm,shows:bunnyCharm,live:chickCharm,games:basketCharm,music:tulipCharm,books:carrotCharm};

function paws(x,y,s,c){const pr=(px,py,r)=>`<g transform="rotate(${r} ${px} ${py})"><ellipse cx="${px}" cy="${py}" rx="${f1(s*.22)}" ry="${f1(s*.32)}" fill="${c}"/><circle cx="${f1(px-s*.17)}" cy="${f1(py-s*.38)}" r="${f1(s*.08)}" fill="${c}"/><circle cx="${px}" cy="${f1(py-s*.44)}" r="${f1(s*.08)}" fill="${c}"/><circle cx="${f1(px+s*.17)}" cy="${f1(py-s*.38)}" r="${f1(s*.08)}" fill="${c}"/></g>`;return pr(x-s*.32,y+s*.25,-14)+pr(x+s*.32,y-s*.15,14)}
function ears(x,y,s){const F='#F7F3EC',PK='#F4A3BD',E='#D8CCBA';const e=(dx,r)=>`<g transform="rotate(${r} ${x+dx} ${y})"><path d="M${f1(x+dx-s*.2)} ${y}C${f1(x+dx-s*.26)} ${f1(y-s*.8)} ${f1(x+dx-s*.12)} ${f1(y-s*1.15)} ${f1(x+dx)} ${f1(y-s*1.15)}C${f1(x+dx+s*.12)} ${f1(y-s*1.15)} ${f1(x+dx+s*.26)} ${f1(y-s*.8)} ${f1(x+dx+s*.2)} ${y}Z" fill="${F}" stroke="${E}" stroke-width="2"/><path d="M${f1(x+dx-s*.09)} ${y}C${f1(x+dx-s*.13)} ${f1(y-s*.6)} ${f1(x+dx-s*.06)} ${f1(y-s*.95)} ${f1(x+dx)} ${f1(y-s*.95)}C${f1(x+dx+s*.06)} ${f1(y-s*.95)} ${f1(x+dx+s*.13)} ${f1(y-s*.6)} ${f1(x+dx+s*.09)} ${y}Z" fill="${PK}"/></g>`;return e(-s*.28,-10)+e(s*.28,12)}
function daisyKnob(x,y,r){let p='';for(let k=0;k<12;k++){const a=k/12*Math.PI*2;p+=`<ellipse cx="${f1(x+Math.cos(a)*r*.58)}" cy="${f1(y+Math.sin(a)*r*.58)}" rx="${f1(r*.42)}" ry="${f1(r*.17)}" transform="rotate(${f1(a*180/Math.PI)} ${f1(x+Math.cos(a)*r*.58)} ${f1(y+Math.sin(a)*r*.58)})" fill="#fff"/>`}return p+`<circle cx="${x}" cy="${y}" r="${f1(r*.36)}" fill="#FFC928" stroke="#C9900A"/>`}
const GREEN='#3D7A4F',LILAC='#C9B2F5';
let RID=0,STATE='week';
function smooth(P,closed){const n=P.length;let d=`M${f1(P[0][0])} ${f1(P[0][1])}`;const g=i=>closed?P[(i+n)%n]:P[Math.max(0,Math.min(n-1,i))];
 for(let i=0;i<(closed?n:n-1);i++){const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);d+=`C${f1(p1[0]+(p2[0]-p0[0])/6)} ${f1(p1[1]+(p2[1]-p0[1])/6)} ${f1(p2[0]-(p3[0]-p1[0])/6)} ${f1(p2[1]-(p3[1]-p1[1])/6)} ${f1(p2[0])} ${f1(p2[1])}`}return d+(closed?'Z':'')}
function groundY(x,ww,wh){return wh-46+Math.sin(x*.018)*4}

/* ---------- the room: soft sage walls in early morning light, pastel paper bunting ---------- */
function room(W2,H2,win,bunting=true){const id='rm'+(RID++);const fl=H2*.8;
 let s=`<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#121A16"/><stop offset="1" stop-color="#1E2A22"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2A2018"/><stop offset="1" stop-color="#0E0B08"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".55"/></radialGradient>
 <radialGradient id="${id}sun" cx="1" cy=".25" r=".9"><stop offset="0" stop-color="#FFE6B0" stop-opacity=".16"/><stop offset="1" stop-color="#FFE6B0" stop-opacity="0"/></radialGradient></defs>`;
 s+=`<rect width="${W2}" height="${H2}" fill="url(#${id}w)"/>`;for(let x=0;x<W2;x+=W2/24)s+=`<rect x="${f1(x)}" y="0" width="${f1(W2/48)}" height="${fl}" fill="#fff" opacity=".014"/>`;
 s+=`<rect y="${f1(fl-H2*.16)}" width="${W2}" height="${f1(H2*.16)}" fill="#000" opacity=".18"/><rect y="${f1(fl-H2*.16)}" width="${W2}" height="4" fill="#33443A" opacity=".8"/>`;
 if(win)s+=windowSVG(win);
 s+=`<rect width="${W2}" height="${H2}" fill="url(#${id}sun)"/>`;
 s+=`<rect y="${fl}" width="${W2}" height="${H2-fl}" fill="url(#${id}f)"/>`;for(let k=1;k<6;k++)s+=`<path d="M0 ${f1(fl+(H2-fl)*k/6)} H${W2}" stroke="#000" stroke-opacity=".25" stroke-width="2"/>`;
 s+=`<rect y="${fl-10}" width="${W2}" height="10" fill="#141A16"/>`;
 if(bunting){const sc=W2/1920;const sw=W2<600?[[0,18,W2,18,26]]:[[430*sc,30*sc,860*sc,30*sc,70*sc],[860*sc,30*sc,1300*sc,30*sc,70*sc]];const FC=['#F6AFC8','#F6D55C','#C9B2F5','#9FD8C0','#A9CCF2'];let c=0;
  sw.forEach(([x1,y1,x2,y2,sag])=>{const N=Math.round((x2-x1)/(W2<600?30:46*sc));const pt=t=>[x1+(x2-x1)*t,y1+(y2-y1)*t+sag*4*t*(1-t)];let wire='';for(let k=0;k<=40;k++){const [x,y]=pt(k/40);wire+=(k?'L':'M')+f1(x)+' '+f1(y)}
   s+=`<path d="${wire}" stroke="#D8CCB4" stroke-width="${f1(Math.max(1.4,2.6*sc))}" fill="none"/>`;
   for(let k=0;k<N;k++){const [ax,ay]=pt((k+.12)/N),[bx,by]=pt((k+.88)/N);const fh=(W2<600?20:40*sc);const mx=(ax+bx)/2,my=(ay+by)/2;const col=FC[(c++)%5];
    s+=`<g class="flag" data-x="${f1(mx)}" data-y="${f1(my)}" data-k="${c}"><path d="M${f1(ax)} ${f1(ay)}L${f1(bx)} ${f1(by)}L${f1(mx)} ${f1(my+fh)}Z" fill="${col}" opacity=".8"/><path d="M${f1(ax)} ${f1(ay)}L${f1(mx)} ${f1(my+fh)}" stroke="#000" stroke-opacity=".12" stroke-width="2"/></g>`}})}
 return {bg:s,vig:`<rect width="${W2}" height="${H2}" fill="url(#${id}v)"/>`}}

/* ---------- the window: a spring garden in the morning ---------- */
function windowSVG([wx,wy,ww,wh]){const id='wn'+(RID++);const r=rnd(41);
 let s=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7FB6E8"/><stop offset=".75" stop-color="#C9E4F2"/><stop offset="1" stop-color="#F2EED2"/></linearGradient><radialGradient id="${id}sun"><stop offset="0" stop-color="#FFF8D8"/><stop offset=".4" stop-color="#FFF1B0" stop-opacity=".6"/><stop offset="1" stop-color="#FFF1B0" stop-opacity="0"/></radialGradient></defs><rect width="${ww}" height="${wh}" fill="url(#${id}s)"/>`;
 s+=`<circle cx="${f1(ww*.2)}" cy="${f1(wh*.17)}" r="${f1(ww*.22)}" fill="url(#${id}sun)"/><circle cx="${f1(ww*.2)}" cy="${f1(wh*.17)}" r="${f1(ww*.06)}" fill="#FFF8DE"/>`;
 for(let k=0;k<3;k++){const cx=r()*ww,cy=wh*(.1+k*.09),w=ww*(.28+r()*.14);s+=`<g class="cloud" data-x="${f1(cx)}" data-v="${f1(3+r()*4)}" data-w="${ww}"><ellipse cx="0" cy="${f1(cy)}" rx="${f1(w*.5)}" ry="${f1(w*.12)}" fill="#fff" opacity=".85"/><ellipse cx="${f1(-w*.15)}" cy="${f1(cy-w*.08)}" rx="${f1(w*.2)}" ry="${f1(w*.13)}" fill="#fff" opacity=".85"/><ellipse cx="${f1(w*.12)}" cy="${f1(cy-w*.1)}" rx="${f1(w*.16)}" ry="${f1(w*.12)}" fill="#fff" opacity=".85"/></g>`}
 /* far hills, a white picket fence, a blossom tree, the lawn */
 s+=`<path d="M0 ${f1(wh*.62)}Q${f1(ww*.3)} ${f1(wh*.5)} ${f1(ww*.6)} ${f1(wh*.6)}T${ww} ${f1(wh*.56)}V${wh}H0Z" fill="#9CCB7E"/><path d="M0 ${f1(wh*.7)}Q${f1(ww*.4)} ${f1(wh*.62)} ${ww} ${f1(wh*.68)}V${wh}H0Z" fill="#7DB862"/>`;
 const tx=ww*.78,ty=wh*.72;s+=`<path d="M${f1(tx-6)} ${f1(ty)}L${f1(tx-4)} ${f1(ty-wh*.2)}L${f1(tx-22)} ${f1(ty-wh*.32)} M${f1(tx-4)} ${f1(ty-wh*.2)}L${f1(tx+18)} ${f1(ty-wh*.34)} M${f1(tx-4)} ${f1(ty-wh*.24)}L${f1(tx-2)} ${f1(ty-wh*.38)}" stroke="#5A3E2C" stroke-width="7" stroke-linecap="round" fill="none"/><path d="M${f1(tx-6)} ${f1(ty)}L${f1(tx-4)} ${f1(ty-wh*.2)}" stroke="#5A3E2C" stroke-width="11" stroke-linecap="round"/>`;
 for(let k=0;k<70;k++){const a=r()*Math.PI*2,d=Math.sqrt(r());const x=tx-4+Math.cos(a)*ww*.2*d,y=ty-wh*.36+Math.sin(a)*wh*.12*d;s+=`<circle cx="${f1(x)}" cy="${f1(y)}" r="${f1(5+r()*6)}" fill="${['#F9C6D6','#F4A8C2','#FFE1EA','#F7B7CC'][k%4]}"/>`}
 for(let x=-4;x<ww;x+=16){s+=`<path d="M${x} ${f1(wh*.79)}v${f1(-wh*.1)}l5 -6 5 6v${f1(wh*.1)}Z" fill="#F4F1EA" stroke="#C9C2B4" stroke-width="1"/>`}s+=`<rect x="0" y="${f1(wh*.715)}" width="${ww}" height="5" fill="#F4F1EA"/><rect x="0" y="${f1(wh*.76)}" width="${ww}" height="5" fill="#F4F1EA"/>`;
 let g=`M0 ${f1(groundY(0,ww,wh)-14)}`;for(let x=0;x<=ww;x+=10)g+=`L${x} ${f1(groundY(x,ww,wh)-14)}`;g+=`V${wh} H0Z`;s+=`<path d="${g}" fill="#5FA84E"/>`;
 let path=`M0 ${f1(groundY(0,ww,wh))}`;for(let x=0;x<=ww;x+=10)path+=`L${x} ${f1(groundY(x,ww,wh))}`;path+=`V${wh}H0Z`;s+=`<path d="${path}" fill="#E8DCC0"/><path d="${path.replace(/V.*$/,'')}" stroke="#C9B994" stroke-width="2" fill="none"/>`;
 for(let k=0;k<26;k++){const x=r()*ww;s+=`<circle cx="${f1(x)}" cy="${f1(groundY(x,ww,wh)-6-r()*12)}" r="2.4" fill="${['#fff','#FFD23F','#F6AFC8'][k%3]}"/>`}
 /* Easter Sunday: painted eggs hidden in the grass */
 s+=`<g class="sundayonly">${[[.1,'#FFB8D2','#7B5BD6','zig'],[.36,'#BFE6FF','#3F7FD0','dots'],[.55,'#FFF1A6','#F08A24','stripes'],[.9,'#C9F2C0','#E8508A','flowers']].map(([t,b,a,p])=>{const x=ww*t;return egg(x,groundY(x,ww,wh)-20,6,8,b,a,p,{nosh:true})+`<path d="M${f1(x-9)} ${f1(groundY(x,ww,wh)-12)}l3 -8 2 7 3 -9 2 9 3 -7 2 8" stroke="#4E9A40" stroke-width="2" fill="none"/>`}).join('')}</g>`;
 let pet='';for(let k=0;k<18;k++)pet+=`<ellipse class="pe" data-x="${f1(tx-60+r()*120)}" data-y="${f1(r()*wh)}" data-v="${f1(14+r()*12)}" data-d="${f1(r()*6)}" data-wh="${wh}" rx="2.6" ry="1.7" fill="${k%2?'#F9C6D6':'#FFE1EA'}"/>`;
 return `<rect x="${wx-14}" y="${wy-14}" width="${ww+28}" height="${wh+28}" rx="6" fill="#26302A"/><svg x="${wx}" y="${wy}" width="${ww}" height="${wh}" overflow="hidden">${s}<g class="walkers" data-w="${ww}" data-h="${wh}">${walkers()}</g>${pet}</svg><rect x="${wx+ww/2-4}" y="${wy}" width="8" height="${wh}" fill="#26302A"/><rect x="${wx}" y="${wy+wh/2-4}" width="${ww}" height="8" fill="#26302A"/><rect x="${wx-22}" y="${wy+wh+12}" width="${ww+44}" height="10" rx="3" fill="#26302A"/>`}

/* ---------- passers-by (feet on the ground at y=0, facing left) ---------- */
const skin='#E8B48A',skin2='#B97E58';
function leg(x,hipY,col,shoe,ph,w=6){return `<g class="lg" data-x="${x}" data-p="${hipY}" data-ph="${ph}"><path d="M${x} ${hipY} L${x} -3" stroke="${col}" stroke-width="${w}" stroke-linecap="round"/><path d="M${x+2} -1 h-8 a3 3 0 0 1 0 -5 h6Z" fill="${shoe}" stroke="${shoe}" stroke-width="2" stroke-linejoin="round"/></g>`}
function face(x,y,r,hair,sk=skin){return `<circle cx="${x}" cy="${y}" r="${r}" fill="${sk}"/><circle cx="${x-r*.45}" cy="${y-r*.1}" r="${r*.13}" fill="#2A1A10"/><path d="M${x-r*.6} ${y+r*.35} q${r*.25} ${r*.2} ${r*.5} 0" stroke="#7A3A20" stroke-width="1.3" fill="none" stroke-linecap="round"/><circle cx="${x-r*.15}" cy="${y+r*.25}" r="${r*.2}" fill="#E07A6A" opacity=".45"/>`+(hair?`<path d="M${x-r} ${y-r*.1} C${x-r} ${y-r*1.3} ${x+r*1.1} ${y-r*1.4} ${x+r} ${y+r*.2} C${x+r*.6} ${y-r*.3} ${x} ${y-r*.6} ${x-r} ${y-r*.1}Z" fill="${hair}"/>`:'')}
/* a wild brown rabbit that hops properly: crouch on the ground, push off with the back legs, front paws land first */
function wildRabbit(){return `<g class="hopper"><g class="hb">
 <ellipse class="hleg" cx="10" cy="-5" rx="15" ry="4.5" fill="#7A5638"/>
 <ellipse cx="8" cy="-18" rx="17" ry="15" fill="#9A7050"/><ellipse cx="-4" cy="-20" rx="20" ry="13" fill="#A57A58"/><ellipse cx="-16" cy="-22" rx="10" ry="12" fill="#C8A684"/>
 <circle cx="24" cy="-22" r="6.5" fill="#fff"/>
 <g class="fpaw"><ellipse cx="-18" cy="-4" rx="6" ry="3" fill="#8A6244"/></g>
 <ellipse cx="-24" cy="-36" rx="11" ry="9" fill="#A57A58" transform="rotate(-12 -24 -36)"/><ellipse cx="-31" cy="-33" rx="5" ry="4.5" fill="#D9BC9C"/>
 <ellipse cx="-14" cy="-56" rx="4.2" ry="15" fill="#8A6244" transform="rotate(24 -14 -44)"/><ellipse cx="-19" cy="-56" rx="4.4" ry="16" fill="#A57A58" transform="rotate(16 -19 -44)"/><ellipse cx="-19" cy="-55" rx="1.9" ry="11" fill="#E8B4A8" transform="rotate(16 -19 -44)"/>
 <circle cx="-27" cy="-38" r="2.2" fill="#1A120C"/><circle cx="-27.6" cy="-38.7" r=".7" fill="#fff"/><circle cx="-35" cy="-34" r="1.4" fill="#C47A7A"/></g></g>`}
/* a mother duck with three ducklings, all waddling */
function duck(x,s,body,bill){return `<g class="dk" data-x="${x}" data-s="${s}" data-ph="${f1(x*.13)}"><g transform="translate(${x} 0) scale(${s})">
 <g class="dfoot" data-ph="0"><path d="M-2 -10v8 M-6 -1h8" stroke="${bill}" stroke-width="2.4" stroke-linecap="round"/></g><g class="dfoot" data-ph="1"><path d="M5 -10v8 M1 -1h8" stroke="${bill}" stroke-width="2.4" stroke-linecap="round"/></g>
 <g class="dbody"><path d="M-14 -18C-14 -30 4 -32 14 -24L24 -30L20 -18C18 -8 -10 -6 -14 -18Z" fill="${body}" stroke="${dk(body,.25)}" stroke-width="1"/><path d="M-4 -22C2 -26 10 -24 12 -18" stroke="${dk(body,.2)}" stroke-width="2" fill="none"/>
 <path d="M-10 -24C-14 -32 -14 -40 -10 -44" stroke="${body}" stroke-width="9" stroke-linecap="round" fill="none"/><circle cx="-11" cy="-46" r="8" fill="${body}" stroke="${dk(body,.25)}" stroke-width="1"/><path d="M-18 -46L-27 -44L-18 -42Z" fill="${bill}" stroke="${dk(bill,.3)}" stroke-width="1"/><circle cx="-13" cy="-48" r="1.6" fill="#1A1A1A"/></g></g></g>`}
function wDucks(){return `<g class="wk" data-off="0" data-s=".9" data-v=".8">${duck(0,1,'#F4F1EA','#F08A24')+duck(32,.5,'#FFE066','#F08A24')+duck(52,.5,'#FFE066','#F08A24')+duck(72,.5,'#FFE066','#F08A24')}</g>`}
function wKid(){/* a kid in a yellow raincoat and red boots, swinging a basket of eggs */
 let s=leg(-4,-30,'#2E3A5A','#C8323C',0,7)+leg(4,-30,'#2E3A5A','#C8323C',1,7);
 s+=`<path d="M-12 -62 h24 l4 34 h-32Z" fill="#F6D24A" stroke="#C9A21A" stroke-width="1"/><path d="M0 -60 v30" stroke="#C9A21A" stroke-width="1.4"/>`;
 s+=`<g class="bswing"><path d="M-4 -54 L-12 -36" stroke="#F6D24A" stroke-width="7" stroke-linecap="round"/><circle cx="-13" cy="-34" r="3.6" fill="${skin}"/><path d="M-22 -26C-22 -40 -4 -40 -4 -26" stroke="#7A4E24" stroke-width="2.4" fill="none"/>
  ${egg(-17,-27,4,5,'#FFB8D2','#7B5BD6','zig',{nosh:true})}${egg(-10,-27,4,5,'#BFE6FF','#3F7FD0','dots',{nosh:true})}<path d="M-24 -26h22l-3 12h-16Z" fill="#B07A40" stroke="#6E4520" stroke-width="1"/><path d="M-23 -21h20M-22 -17h18" stroke="#7A4E24" stroke-width="1.2"/></g>`;
 s+=face(0,-72,10,'#5A3418')+`<path d="M-11 -78 C-11 -94 11 -94 11 -78 L15 -76 H-15Z" fill="#F6D24A" stroke="#C9A21A" stroke-width="1"/>`;
 return `<g class="wk" data-off="10" data-s=".95">${s}</g>`}
function wTulips(){/* a grown-up in a spring coat carrying a bunch of tulips */
 const H=1;let s=leg(-5,-44,'#3A4A6A','#5A3A2A',0)+leg(5,-44,'#3A4A6A','#5A3A2A',1);
 s+=`<path d="M-14 -90 h28 l6 50 h-40Z" fill="#E88AA8"/><path d="M0 -90 v48" stroke="#000" stroke-opacity=".15" stroke-width="2"/><path d="M-14 -50h28" stroke="#C86A88" stroke-width="3"/>`;
 s+=`<path d="M-8 -84 L-20 -66" stroke="#D97A98" stroke-width="7" stroke-linecap="round"/><circle cx="-21" cy="-64" r="3.6" fill="${skin2}"/>`;
 s+=`<path d="M-21 -64 L-28 -92 M-21 -64 L-22 -96 M-21 -64 L-15 -92" stroke="#3E8A45" stroke-width="2.2"/>${tulip(-29,-90,'#FF6F91',-10,.45)}${tulip(-22,-95,'#FFD23F',0,.45)}${tulip(-14,-90,'#fff',10,.45)}<path d="M-25 -70h8" stroke="#C9B2F5" stroke-width="4" stroke-linecap="round"/>`;
 s+=face(0,-102,11,'#2A1A10',skin2)+`<ellipse cx="0" cy="-110" rx="20" ry="4" fill="#F2E3C2"/><path d="M-11 -110 C-11 -124 11 -124 11 -110Z" fill="#F2E3C2"/><path d="M-11 -112h22" stroke="#9AC8E8" stroke-width="3"/>`;
 return `<g class="wk" data-off="16" data-s=".92">${s}</g>`}
function walkers(){return wDucks()+wKid()+wTulips()+`<g class="wk wr" data-off="23" data-s=".5">${wildRabbit()}</g>`}

/* ---------- the Easter table: an egg tree in a jug, a basket, a foil chocolate bunny; the pet rabbit on the floor ---------- */
const BR=[/* branches: points from the jug mouth upward */
 [[250,490],[236,420],[210,350],[186,290],[170,236]],
 [[254,490],[262,410],[280,340],[308,280],[336,232]],
 [[246,492],[214,446],[170,412],[126,394],[92,372]],
 [[258,492],[300,454],[352,428],[400,404],[440,370]],
 [[250,490],[250,400],[246,320],[250,250],[256,190]],
 [[236,420],[200,402],[160,372]],[[262,410],[300,392],[340,364]],[[246,320],[220,290],[204,262]],[[280,340],[300,312],[296,286]]];
const HANG=[[0,3,'#FFB8D2','#7B5BD6','zig'],[1,3,'#BFE6FF','#3F7FD0','dots'],[2,2,'#FFF1A6','#F08A24','stripes'],[3,2,'#C9F2C0','#E8508A','flowers'],[4,3,'#E3D5FF','#4AA8C8','waves'],[5,2,'#FFD9B8','#5AAE52','checks'],[6,2,'#F6AFC8','#3F7FD0','dots'],[3,3,'#BFE6FF','#F08A24','zig'],[2,3,'#E3D5FF','#E8508A','stripes'],[1,2,'#FFF1A6','#5AAE52','waves'],[7,1,'#C9F2C0','#7B5BD6','checks']];
function branchPath(P){return smooth(P,false)}
function jug(cx,by,h,w){const p=u('jg');return `<defs><linearGradient id="${p}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#6F9A7E"/><stop offset=".35" stop-color="#A9CDB4"/><stop offset=".6" stop-color="#7FA88D"/><stop offset="1" stop-color="#3E6450"/></linearGradient></defs>
 <path d="M${f1(cx-w*.28)} ${f1(by-h)}C${f1(cx-w*.3)} ${f1(by-h*.85)} ${f1(cx-w*.56)} ${f1(by-h*.6)} ${f1(cx-w*.5)} ${f1(by-h*.3)}C${f1(cx-w*.46)} ${f1(by-h*.08)} ${f1(cx-w*.36)} ${by} ${f1(cx-w*.3)} ${by}H${f1(cx+w*.3)}C${f1(cx+w*.36)} ${by} ${f1(cx+w*.46)} ${f1(by-h*.08)} ${f1(cx+w*.5)} ${f1(by-h*.3)}C${f1(cx+w*.56)} ${f1(by-h*.6)} ${f1(cx+w*.3)} ${f1(by-h*.85)} ${f1(cx+w*.28)} ${f1(by-h)}Z" fill="url(#${p})" stroke="#2E4A3A" stroke-width="1.6"/>
 <ellipse cx="${cx}" cy="${f1(by-h)}" rx="${f1(w*.3)}" ry="${f1(w*.07)}" fill="#24382C" stroke="#2E4A3A" stroke-width="1.4"/><path d="M${f1(cx-w*.3)} ${f1(by-h)}q${f1(-w*.06)} ${f1(-w*.04)} ${f1(-w*.1)} ${f1(-w*.02)}" stroke="#7FA88D" stroke-width="4" fill="none"/>
 <path d="M${f1(cx+w*.48)} ${f1(by-h*.78)}C${f1(cx+w*.82)} ${f1(by-h*.78)} ${f1(cx+w*.8)} ${f1(by-h*.36)} ${f1(cx+w*.48)} ${f1(by-h*.36)}" stroke="#5E8A70" stroke-width="${f1(w*.09)}" fill="none" stroke-linecap="round"/>
 <path d="M${f1(cx-w*.3)} ${f1(by-h*.62)}C${f1(cx-w*.34)} ${f1(by-h*.4)} ${f1(cx-w*.3)} ${f1(by-h*.2)} ${f1(cx-w*.22)} ${f1(by-h*.08)}" stroke="#fff" stroke-width="${f1(w*.06)}" opacity=".35" fill="none" stroke-linecap="round"/>
 <path d="M${f1(cx-w*.45)} ${f1(by-h*.45)}H${f1(cx+w*.45)}" stroke="#E8DFC8" stroke-width="3" opacity=".6"/><path d="M${f1(cx-w*.46)} ${f1(by-h*.4)}H${f1(cx+w*.46)}" stroke="#C9B2F5" stroke-width="2" opacity=".55"/>`}
function eggTree(){let s='';const R=rnd(23);
 BR.forEach((P,k)=>{const w=k<5?7:4;s+=`<path d="${branchPath(P)}" stroke="#4A3428" stroke-width="${w}" fill="none" stroke-linecap="round"/><path d="${branchPath(P)}" stroke="#7A5A44" stroke-width="${f1(w*.35)}" fill="none" stroke-linecap="round" transform="translate(-1 -1)"/>`;
  P.forEach((q,i)=>{if(!i)return;/* pussy-willow buds and new leaves along each branch */for(let j=0;j<2;j++){const a=(R()-.5)*2.4,side=j?1:-1;const bx=q[0]+side*5,by=q[1]+R()*8;
   if(R()<.55)s+=`<ellipse cx="${f1(bx)}" cy="${f1(by)}" rx="3.4" ry="5.6" transform="rotate(${f1(a*30)} ${f1(bx)} ${f1(by)})" fill="#E9E4DA" stroke="#B9B0A2" stroke-width=".8"/><ellipse cx="${f1(bx)}" cy="${f1(by+4)}" rx="2" ry="1.4" fill="#6A4A34"/>`;
   else s+=`<path d="M${f1(bx)} ${f1(by)}q${f1(side*8)} -4 ${f1(side*14)} -1 q${f1(-side*6)} 6 ${f1(-side*14)} 1Z" fill="#7CCB6A" stroke="#4E9A40" stroke-width=".8"/>`}})});
 HANG.forEach(([b,i,base,acc,pat],k)=>{const [x,y]=BR[b][i];const L=22+(k%3)*12;const rx=15,ry=20;
  s+=`<g class="swing" data-x="${x}" data-y="${y}" data-k="${k}"><path d="M${x} ${y}V${y+L}" stroke="${lt(acc,.3)}" stroke-width="1.6"/>${egg(x,y+L+ry,rx,ry,base,acc,pat)}<path d="M${x-4} ${y+L+1}q4 -6 8 0 M${x-4} ${y+L+1}q4 5 8 0" stroke="${lt(acc,.3)}" stroke-width="1.8" fill="none"/></g>`});
 return s}
function chocBunny(cx,by,s,bitten){/* a chocolate bunny in crinkled gold foil, sitting, with a red ribbon and bell */const p=u('cb');
 const body=`M${cx+22*s} ${by}C${cx+34*s} ${by} ${cx+38*s} ${by-30*s} ${cx+26*s} ${by-52*s}C${cx+20*s} ${by-64*s} ${cx+8*s} ${by-70*s} ${cx-2*s} ${by-74*s}C${cx-8*s} ${by-96*s} ${cx-12*s} ${by-118*s} ${cx-6*s} ${by-124*s}C${cx} ${by-128*s} ${cx+4*s} ${by-112*s} ${cx+4*s} ${by-96*s}C${cx+10*s} ${by-112*s} ${cx+18*s} ${by-124*s} ${cx+22*s} ${by-118*s}C${cx+26*s} ${by-110*s} ${cx+14*s} ${by-88*s} ${cx+8*s} ${by-74*s}C${cx-10*s} ${by-74*s} ${cx-26*s} ${by-66*s} ${cx-26*s} ${by-52*s}C${cx-26*s} ${by-40*s} ${cx-14*s} ${by-36*s} ${cx-18*s} ${by-20*s}C${cx-22*s} ${by-8*s} ${cx-20*s} ${by} ${cx-12*s} ${by}Z`;
 let cr='';const R=rnd(9);for(let k=0;k<26;k++){const x=cx+(R()-.5)*50*s,y=by-R()*110*s;cr+=`<path d="M${f1(x)} ${f1(y)}l${f1((R()-.5)*10*s)} ${f1((R()-.5)*10*s)}l${f1((R()-.5)*10*s)} ${f1((R()-.5)*10*s)}" stroke="${R()<.5?'#FFF4C8':'#8A6418'}" stroke-width="${f1(1.2*s)}" opacity=".8" fill="none"/>`}
 const bite=bitten?`<path d="M${cx+4*s} ${by-124*s}c-6 8 4 14 -2 20 c6 2 10 -6 16 -2 c-2 -8 4 -16 -2 -20Z" fill="#5A3416"/><path d="M${cx+4*s} ${by-124*s}c-6 8 4 14 -2 20 c6 2 10 -6 16 -2" stroke="#3A2010" stroke-width="1.2" fill="none"/>`:'';
 return `<defs><linearGradient id="${p}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFF1C2"/><stop offset=".35" stop-color="#E8B84A"/><stop offset=".7" stop-color="#C9962E"/><stop offset="1" stop-color="#7A5810"/></linearGradient><clipPath id="${p}c"><path d="${body}"/></clipPath>${sh(p+'s',4)}</defs>
 <g filter="url(#${p}s)"><path d="${body}" fill="url(#${p})" stroke="#6A4A10" stroke-width="1.2"/></g><g clip-path="url(#${p}c)">${cr}</g>${bite}
 <circle cx="${cx-6*s}" cy="${by-66*s}" r="${f1(2.6*s)}" fill="#6A4A10"/><path d="M${cx-18*s} ${by-58*s}q2 2 4 0" stroke="#6A4A10" stroke-width="${f1(1.4*s)}" fill="none"/>
 <path d="M${cx-14*s} ${by-48*s}Q${cx} ${by-42*s} ${cx+20*s} ${by-50*s}" stroke="#C8323C" stroke-width="${f1(5*s)}" fill="none"/><path d="M${cx-4*s} ${by-45*s}l-8 10 6 -2 2 6Z M${cx-4*s} ${by-45*s}l6 10 1 -6 5 2Z" fill="#C8323C"/><circle cx="${cx-4*s}" cy="${by-40*s}" r="${f1(4*s)}" fill="#E8B84A" stroke="#6A4A10" stroke-width="1"/>`}
function petRabbit(x,y,s){/* the house rabbit, sitting on the floor: fawn with a white chest. Nose, ears and eyes move */
 return `<g class="pet" data-x="${x}" data-y="${y}" data-s="${s}"><g transform="translate(${x} ${y}) scale(${s})"><g class="pbody">
 <ellipse cx="0" cy="2" rx="62" ry="7" fill="#000" opacity=".3"/>
 <ellipse cx="12" cy="-6" rx="30" ry="8" fill="#B88A60"/>
 <ellipse cx="20" cy="-36" rx="36" ry="34" fill="#C99A6C"/><ellipse cx="-4" cy="-38" rx="44" ry="31" fill="#D1A678"/>
 <circle cx="54" cy="-40" r="13" fill="#F4EEE4"/><circle cx="51" cy="-44" r="6" fill="#fff"/>
 <ellipse cx="-30" cy="-42" rx="22" ry="27" fill="#F4EEE4"/><ellipse cx="-36" cy="-6" rx="11" ry="6" fill="#F4EEE4"/><ellipse cx="-22" cy="-5" rx="11" ry="6" fill="#ECE4D6"/>
 <path d="M10 -64C24 -60 38 -46 40 -30" stroke="#fff" stroke-width="4" opacity=".25" fill="none" stroke-linecap="round"/>
 <g class="phead"><g class="pear" data-ex="-34" data-ey="-92" data-k="0"><ellipse cx="-24" cy="-130" rx="11" ry="38" fill="#B88A60" transform="rotate(20 -32 -94)"/></g>
 <g class="pear" data-ex="-40" data-ey="-92" data-k="1"><ellipse cx="-34" cy="-132" rx="12" ry="40" fill="#D1A678" transform="rotate(10 -40 -94)"/><ellipse cx="-34" cy="-130" rx="5.5" ry="30" fill="#E8B4A8" transform="rotate(10 -40 -94)"/></g>
 <ellipse cx="-46" cy="-80" rx="27" ry="23" fill="#D1A678" transform="rotate(-10 -46 -80)"/><ellipse cx="-62" cy="-72" rx="15" ry="12" fill="#F4EEE4"/>
 <ellipse cx="-50" cy="-84" rx="6" ry="6.5" fill="#1E1410"/><circle cx="-52" cy="-86.5" r="2" fill="#fff"/><g class="plid"><ellipse cx="-50" cy="-84" rx="6.6" ry="7" fill="#C99A6C"/></g>
 <g class="pnose"><path d="M-75 -78l5 -1 -2 5Z" fill="#D98A96"/><path d="M-70 -74q-2 5 -6 6 M-70 -74q2 5 -1 7" stroke="#8A6A5A" stroke-width="1.2" fill="none"/></g>
 <path d="M-66 -72l-24 -6 M-66 -70l-24 2 M-62 -71l-20 -10" stroke="#fff" stroke-width=".9" opacity=".7"/></g></g></g></g>`}
function easterTable(o={}){/* absolute TV coordinates: table top at y=640, floor at 864 */let s='';
 s+=`<defs><linearGradient id="tbw" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8A6444"/><stop offset="1" stop-color="#5A3E28"/></linearGradient></defs>`;
 /* table: turned legs, top, a linen runner with a scalloped edge */
 [[110,0],[430,0],[150,1],[392,1]].forEach(([x,b])=>{s+=`<path d="M${x-9} 652 L${x-6} 760 Q${x-10} 790 ${x-5} 820 L${x-4} 864 H${x+4} L${x+5} 820 Q${x+10} 790 ${x+6} 760 L${x+9} 652Z" fill="${b?'#4A3220':'#6E4E34'}"/>`});
 s+=`<rect x="80" y="636" width="380" height="20" rx="4" fill="url(#tbw)" stroke="#3A2616"/><rect x="88" y="656" width="364" height="18" fill="#4A3220"/>`;
 let sc='';for(let x=180;x<380;x+=20)sc+=`a10 10 0 0 0 20 0`;s+=`<path d="M170 634 H390 V686 H380 ${sc.split('a').slice(1).reverse().map(()=>'a10 10 0 0 1 -20 0').join(' ')} H170Z" fill="#EFE8D8" opacity=".95"/><path d="M178 640 H382" stroke="#C9B2F5" stroke-width="2.4" opacity=".8"/><path d="M178 678 H382" stroke="#9FD8C0" stroke-width="2" opacity=".8" stroke-dasharray="4 4"/>`;
 s+=eggTree()+jug(250,636,150,118);
 s+=`<g transform="translate(0 0)">${basket(400,520,104)}</g>`;
 s+=chocBunny(132,636,1,o.bitten);
 if(o.day){s+=`<g class="gold" data-x="320" data-y="630">${goldenEgg(320,606)}</g>`}
 s+=`<ellipse cx="530" cy="868" rx="70" ry="8" fill="#000" opacity=".25"/>`+petRabbit(530,864,.95);
 /* a pot of tulips on the floor behind the table leg */
 s+=`<path d="M40 864 L32 812 H92 L84 864Z" fill="#B8643E" stroke="#6E3A20"/><rect x="28" y="804" width="68" height="12" rx="3" fill="#C8744E" stroke="#6E3A20"/><path d="M50 806 Q44 760 46 730 M62 806 Q64 750 60 716 M74 806 Q82 768 80 736" stroke="#3E8A45" stroke-width="3.4" fill="none"/><path d="M46 806C34 790 30 770 34 756C42 770 48 786 50 806Z M78 806C90 790 94 774 90 760C82 774 76 790 74 806Z" fill="#5BB35E"/>`+tulip(46,736,'#FF6F91',-8,1)+tulip(60,722,'#FFD23F',0,1)+tulip(80,742,'#F6AFC8',8,1);
 return s}
function goldenEgg(cx,cy){const p=u('ge');return `<defs><radialGradient id="${p}" cx=".35" cy=".3" r=".9"><stop offset="0" stop-color="#FFF6D0"/><stop offset=".5" stop-color="#EBC35A"/><stop offset="1" stop-color="#8A6418"/></radialGradient></defs>
 <g class="gbot"><path d="${eggPath(cx,cy,22,29)}" fill="url(#${p})" stroke="#6A4A10" stroke-width="1.2"/><circle cx="${cx-8}" cy="${cy-12}" r="5" fill="#fff" opacity=".7"/></g>
 <g class="gcrack" opacity="0"><path d="M${cx-21} ${cy-4}l7 -6 6 6 7 -7 6 6 7 -5 8 4" stroke="#5A3A08" stroke-width="2" fill="none"/></g>
 <g class="gspk" opacity="0">${Array.from({length:10},(_,k)=>{const a=k/10*Math.PI*2;return `<path d="M${f1(cx+Math.cos(a)*30)} ${f1(cy-20+Math.sin(a)*30)}L${f1(cx+Math.cos(a)*48)} ${f1(cy-20+Math.sin(a)*48)}" stroke="${k%2?'#FFE08A':'#fff'}" stroke-width="3" stroke-linecap="round"/>`}).join('')}</g>`}
  // ---------- the six hidden eggs (one on each door page on Easter Sunday), a different pattern each ----------
  const HUNT_EGGS = { movies: ["#FFB8D2", "#7B5BD6", "zig"], shows: ["#BFE6FF", "#3F7FD0", "dots"], live: ["#FFF1A6", "#F08A24", "stripes"], games: ["#C9F2C0", "#E8508A", "flowers"], music: ["#E3D5FF", "#4AA8C8", "waves"], books: ["#FFD9B8", "#5AAE52", "checks"] };
  /** a hidden egg peeking out of a tuft of grass, centred on x=0, sitting on y=0 */
  function huntEgg(door) {
    const [b, a, p] = HUNT_EGGS[door] || HUNT_EGGS.movies;
    let grass = ""; const R = rnd(DOOR_KEYS.indexOf(door) + 3);
    for (let k = 0; k < 13; k++) { const x = -24 + k * 4; grass += `<path d="M${f1(x)} 0q${f1((R() - .5) * 6)} ${f1(-6 - R() * 6)} ${f1((R() - .5) * 10)} ${f1(-9 - R() * 9)}" stroke="${k % 2 ? "#7CCB6A" : "#4E9A40"}" stroke-width="3" fill="none" stroke-linecap="round"/>`; }
    return egg(0, -24, 17, 22, b, a, p) + grass;
  }
  /** the small Easter touches as pictures (tools/easter/pictures.js) */
  const touches = {
    ears: () => ears(0, 0, 80),
    knob: () => `<g transform="rotate(18 0 0)">${egg(0, 0, 15, 20, "#FFF1A6", "#E8508A", "stripes")}</g>`,
    daisy: () => daisyKnob(0, 0, 24),
    loader: () => `<g transform="rotate(-12 0 40)">${egg(0, 2, 34, 46, "#BFE6FF", "#3F7FD0", "dots")}<path d="M-22 -12l9 7 7-9 8 8 7-6 9 6" stroke="#2A3A4A" stroke-width="2.4" fill="none" stroke-linejoin="round"/></g>`,
    ep: () => egg(0, 0, 13, 17, "#FFB8D2", "#7B5BD6", "zig", { nosh: true }),
    ready: () => `<g>${chick(0, -4, 12)}${shell(0, 6, 16, 13, false)}</g>`,
    way: () => paws(0, 0, 30, LILAC),
    live: () => tulip(0, 14, "#FF6F91", 0, 1) + `<path d="M0 18V26" stroke="#3E8A45" stroke-width="3"/>`,
  };

  /** The two shapes: wide (TVs, computers) and tall (phones) */
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1590, 150, 280, 350], table: "translate(0 -129.6) scale(1.15)" },
    tall: { w: 390, h: 844, win: null, table: "translate(0 326) scale(.6)" },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t), crack(t) } which poses everything at t seconds.
   * opts: { layout: "wide"|"tall", day: Easter Sunday, open: the golden egg is already open, align }
   */
  function build(svg, opts) {
    const o = opts || {};
    const L = LAYOUTS[o.layout || "wide"];
    const day = o.day === undefined ? isSunday(new Date()) : !!o.day;
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", o.align || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win);
    let s = rm.bg + `<g transform="${L.table}">${easterTable({ day, bitten: day })}</g>` + rm.vig;
    if (!day) s = s.replace('class="sundayonly"', 'class="sundayonly" display="none"');
    svg.innerHTML = GDEF + s;
    return pose(svg, !!o.open);
  }

  /* gathers every moving part, and returns at(t) */
  function pose(root, open) {
    const q = (s) => [...root.querySelectorAll(s)];
    const flags = q(".flag"), swings = q(".swing");
    const pets = q(".pet").map((g) => ({ b: g.querySelector(".pbody"), h: g.querySelector(".phead"), n: g.querySelector(".pnose"), l: g.querySelector(".plid"), e: [...g.querySelectorAll(".pear")] }));
    const clouds = q(".cloud").map((c, k) => ({ c, w: +c.dataset.w, x: +c.dataset.x, ph: k * 2.1 }));
    const petals = q(".pe").map((p, k) => ({ p, x: +p.dataset.x, y: +p.dataset.y, d: +p.dataset.d, H: +p.dataset.wh, n: 1 + (k % 2) }));
    const golds = q(".gold").map((g) => ({ g, x: +g.dataset.x, y: +g.dataset.y, c: g.querySelector(".gcrack"), k: g.querySelector(".gspk"), b: g.querySelector(".gbot") }));
    const walkers = q(".walkers").map((W) => ({ ww: +W.dataset.w, wh: +W.dataset.h, wk: [...W.querySelectorAll(".wk")] }));
    const sFlag = wq(1.1), sHead = wq(0.6), sNose = wq(26), sCloud = TAU / LOOP, sSpark = wq(10);
    const sSw = [wq(1.1), wq(1.27), wq(1.44)], sSw2 = wq(0.37);
    let crackAt = null;
    function at(t) {
      flags.forEach((f) => { const k = +f.dataset.k; f.setAttribute("transform", `rotate(${f1(Math.sin(t * sFlag + k * 0.8) * 3)} ${f.dataset.x} ${f.dataset.y})`); });
      swings.forEach((g) => { const k = +g.dataset.k; const a = Math.sin(t * sSw[k % 3] + k * 1.3) * 5 + Math.sin(t * sSw2 + k) * 2; g.setAttribute("transform", `rotate(${f1(a)} ${g.dataset.x} ${g.dataset.y})`); });
      pets.forEach((P) => {
        /* nose twitches in quick bursts, ears turn now and then, a blink every few seconds, a small hop on the spot every 9 seconds */
        const tw = (t % 2.4) < 1.1 ? Math.abs(Math.sin(t * sNose)) * 1.6 : 0; P.n.setAttribute("transform", `translate(0 ${f1(-tw)})`);
        P.e.forEach((e, i) => { const c = (t + i * 1.7) % 6; const a = c < 0.5 ? Math.sin(c / 0.5 * Math.PI) * (i ? -14 : 10) : 0; e.setAttribute("transform", `rotate(${f1(a)} ${e.dataset.ex} ${e.dataset.ey})`); });
        P.l.setAttribute("opacity", (t % 4.5) < 0.16 ? "1" : "0");
        const hc = t % 9; let lift = 0, tilt = 0; if (hc > 8.2) { const e = (hc - 8.2) / 0.8; lift = Math.sin(e * Math.PI) * 22; tilt = Math.sin(e * Math.PI * 2) * -6; }
        P.b.setAttribute("transform", `translate(0 ${f1(-lift)}) rotate(${f1(tilt)} 0 -30)`); P.h.setAttribute("transform", `rotate(${f1(Math.sin(t * sHead) * 3)} -40 -70)`);
      });
      clouds.forEach((C) => C.c.setAttribute("transform", `translate(${f1(Math.sin(t * sCloud + C.ph) * C.w * 0.12)} 0)`));
      petals.forEach((P) => {
        const span = P.H + 10; const y = ((P.y + t * span * P.n / LOOP) % span) - 5;
        const x = P.x - y * 0.25 + Math.sin(t * wq(1.4) + P.d) * 10;
        P.p.setAttribute("cx", f1(x)); P.p.setAttribute("cy", f1(y)); P.p.setAttribute("transform", `rotate(${f1((t * 360 * P.n * 8 / LOOP + P.d * 60) % 360)} ${f1(x)} ${f1(y)})`);
      });
      golds.forEach((Gd) => {
        // the golden egg: shut, cracking open (crackAt), or open with sparkles twinkling
        let h = open ? 9 : crackAt === null ? -1 : t - crackAt;
        if (h < 0) { Gd.g.setAttribute("transform", ""); Gd.c.setAttribute("opacity", "0"); Gd.k.setAttribute("opacity", "0"); Gd.b.setAttribute("transform", ""); return; }
        const wob = h < 2.2 ? Math.sin(h * 18) * 8 * Math.min(1, h) : 0; Gd.g.setAttribute("transform", `rotate(${f1(wob)} ${Gd.x} ${Gd.y})`);
        Gd.c.setAttribute("opacity", h > 1.4 ? "1" : "0");
        Gd.b.setAttribute("transform", h > 2.2 ? `translate(0 ${f1(-Math.min(1, (h - 2.2) / 0.4) * 10)})` : "");
        Gd.k.setAttribute("opacity", h > 2.2 ? (0.6 + 0.4 * Math.sin(t * sSpark)).toFixed(2) : "0");
      });
      walkers.forEach(({ ww, wh, wk }) => {
        const v = (ww + 300) / 9;
        wk.forEach((g) => {
          const off = +g.dataset.off, sc = 1.5 * (+g.dataset.s); const tt = ((t + LOOP - off) % LOOP);
          if (g.classList.contains("wr")) {
            /* the wild rabbit: each hop is .55 s in the air, then .45 s sitting still */
            const hopT = 1, air = 0.55, hopLen = 40; const n = Math.floor(tt / hopT), ph = tt % hopT; const inAir = ph < air; const e = inAir ? ph / air : 1;
            const x = ww + 120 - (n + (inAir ? e : 1)) * hopLen * sc / 1.5;
            const y = groundY(x, ww, wh) + 1 - (inAir ? Math.sin(e * Math.PI) * 24 : 0) * sc / 1.5; const rot = inAir ? (e < 0.5 ? -14 + e * 20 : -4 + (e - 0.5) * 40) : 0;
            g.setAttribute("transform", `translate(${f1(x)} ${f1(y)}) scale(${f1(sc)})`);
            g.querySelector(".hb").setAttribute("transform", `rotate(${f1(-rot)} 0 -10)`);
            g.querySelector(".hleg").setAttribute("transform", inAir ? `translate(${f1(8 * Math.sin(e * Math.PI))} ${f1(2 * Math.sin(e * Math.PI))}) rotate(${f1(20 * Math.sin(e * Math.PI))} 0 -5)` : "");
            g.querySelector(".fpaw").setAttribute("transform", inAir ? `translate(${f1(-6 * Math.sin(e * Math.PI))} ${f1(-4 * Math.sin(e * Math.PI))})` : "");
            return;
          }
          const vv = v * (+(g.dataset.v || 1)); const x = ww + 150 - tt * vv; const y = groundY(x, ww, wh) + 2;
          if (g.querySelector(".dk")) {
            g.setAttribute("transform", `translate(${f1(x)} ${f1(y)}) scale(${f1(sc)})`);
            g.querySelectorAll(".dk").forEach((d) => { const ph = +d.dataset.ph; const w = Math.sin(tt * 9 + ph); d.querySelector(".dbody").setAttribute("transform", `rotate(${f1(w * 6)} 0 -6)`); d.querySelectorAll(".dfoot").forEach((f) => { const up = Math.max(0, Math.sin(tt * 9 + ph + (+f.dataset.ph) * Math.PI)) * 3; f.setAttribute("transform", `translate(0 ${f1(-up)})`); }); });
            return;
          }
          const bob = Math.abs(Math.sin(tt * 7)) * 1.5; g.setAttribute("transform", `translate(${f1(x)} ${f1(y - bob)}) scale(${f1(sc)})`);
          g.querySelectorAll(".lg").forEach((l) => { const sw = Math.sin(tt * 7 + (+l.dataset.ph) * Math.PI) * 22; l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} ${l.dataset.p})`); });
          const b = g.querySelector(".bswing"); if (b) b.setAttribute("transform", `rotate(${f1(Math.sin(tt * 7) * 9)} -4 -54)`);
        });
      });
    }
    /** starts the golden egg cracking open at t seconds (it then stays open) */
    const crack = (t) => { if (!open && crackAt === null) crackAt = t; };
    return { at, crack };
  }

  /** Plays a built room live until the element leaves the page. Returns the clock (seconds) it uses. */
  function play(svg, scene) {
    const start = performance.now() - 4000;
    const clock = () => (performance.now() - start) / 1000;
    const tick = () => { if (!svg.isConnected) return; scene.at(clock()); requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
    return clock;
  }

  // ---------- web: the hidden egg on each door page (Easter Sunday only) ----------
  const doorOf = (hash) => {
    if (/^#\/(movies$|grid\/movies|browse\/movies)/.test(hash)) return "movies";
    if (/^#\/(shows$|grid\/shows|browse\/shows)/.test(hash)) return "shows";
    if (/^#\/live$/.test(hash)) return "live";
    if (/^#\/(games$|console\/)/.test(hash)) return "games";
    if (/^#\/(music$|msearch|mlibrary|mthemes\/|songs$|liked$|mgrid\/)/.test(hash)) return "music";
    if (/^#\/books(\?|$)/.test(hash)) return "books";
    return null;
  };
  function paintHunt() {
    const B = G.B; const page = document.getElementById("page"); if (!page) return;
    const old = page.querySelector(":scope > .ehunt");
    const now = new Date();
    const door = B && B.me && B.eaOn && B.eaOn() && isSunday(now) ? doorOf(location.hash || "") : null;
    const want = door && !hunt.found(now).includes(door) ? door : null;
    if (old && old.dataset.door === want) return;
    if (old) old.remove();
    if (!want) return;
    const btn = document.createElement("button");
    btn.className = "ehunt"; btn.dataset.door = want; btn.type = "button";
    btn.setAttribute("aria-label", `A hidden Easter egg on the ${DOOR_NAMES[want]} page`);
    btn.innerHTML = `<img src="web/img/ea/ea_hunt_${want}.png" alt="">`;
    btn.onclick = () => {
      const n = hunt.find(want, new Date());
      btn.classList.add("got"); setTimeout(() => btn.remove(), 600);
      const msg = n >= 6 ? "You found all six! The golden egg is opening on Who's watching" : `You found the ${DOOR_NAMES[want]} egg! ${n} of 6`;
      if (B && B.toast) B.toast(msg);
    };
    page.appendChild(btn);
  }
  if (typeof document !== "undefined" && typeof location !== "undefined") {
    const startHunt = () => {
      const page = document.getElementById("page"); if (!page) return;
      new MutationObserver(() => { if (!page.querySelector(":scope > .ehunt")) paintHunt(); }).observe(page, { childList: true });
      addEventListener("hashchange", () => setTimeout(paintHunt, 0));
      paintHunt();
    };
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", startHunt); else startHunt();
  }

  const kit = { GDEF, egg, CHARM, huntEgg, touches, ears, daisyKnob, paws, chick, shell, tulip };
  G.BenflixEaster = { LOOP, season, seasonOf, easterSunday, isSunday, variant, greeting, countdown, hunt, build, play, pose, LAYOUTS, kit, DOOR_KEYS, DOOR_NAMES, paintHunt };
})(typeof window !== "undefined" ? window : globalThis);
