/* Benflix Earth Day: the Who's watching room (approved 2026-11-17, thread "Design an Earth Day theme for Benflix").
 *
 * A dim spring-evening room with a plant corner at the bottom left: a big monstera in a woven basket, a hanging
 * pothos, and a side table with a turning desk globe, a watering can and a seedling in a pot that grows through
 * Earth week (day 1 a sprout, day 5 a bud, day 7 a sunflower). A window on the right shows a spring dusk: a
 * blossom tree, tulips, birds and fireflies, and people walking past on the path. On Earth Day itself (day 1)
 * two of them are planting a young tree by the path, and it stays there for the rest of the week.
 * Earth week is 22 to 28 April; when Easter week touches it, it starts the day after Easter Sunday instead.
 *
 * Like feast.js, the same drawing is used live on the web and by tools/earthday/record.js, which records it as
 * looping videos for the TV and phone app (one per day of the week). Everything is a function of the time t
 * (seconds) and repeats every LOOP seconds, so a video of one loop joins up with no jump.
 * The drawing code comes straight from the approved design sheet (earthday-2-whos-watching.html).
 */
(function (G) {
  "use strict";
  const LOOP = 30;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;

  // ---------- dates ----------
  /** Easter Sunday (the usual Gregorian formula, the same as the app's) */
  function easter(y) {
    const a = y % 19, b = Math.floor(y / 100), c = y % 100, d = Math.floor(b / 4), e = b % 4, f = Math.floor((b + 8) / 25);
    const g = Math.floor((b - f + 1) / 3), h = (19 * a + b - d - g + 15) % 30, i = Math.floor(c / 4), k = c % 4;
    const l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451);
    const n = h + l - 7 * m + 114;
    return new Date(y, Math.floor(n / 31) - 1, (n % 31) + 1);
  }
  const dayNo = (d) => Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 864e5);
  /** The first day of Earth week: 22 April, or the day after Easter Sunday when Easter week (the 6 days before
   *  Easter Sunday and the day itself) touches 22 to 28 April */
  function start(year) {
    const e = easter(year), earth = new Date(year, 3, 22);
    const clash = dayNo(e) >= dayNo(earth) && dayNo(e) - 6 <= dayNo(earth) + 6;
    return clash ? new Date(year, e.getMonth(), e.getDate() + 1) : earth;
  }
  /** Which day of Earth week it is (1 to 7), or 0 outside it */
  function day(d) {
    const n = dayNo(d) - dayNo(start(d.getFullYear())) + 1;
    return n >= 1 && n <= 7 ? n : 0;
  }
  const season = (d) => day(d) > 0;
  /** "Happy Earth Day" all week */
  const greeting = () => "Happy Earth Day";
  /** "Earth week, day 3" */
  const line = (d) => (day(d) ? `Earth week, day ${day(d)}` : "");

  // ---------- drawing kit (from the approved design sheet) ----------
  const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
  const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
  const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
  const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
  const f1=v=>(+v).toFixed(1);
  function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
  let RID=0;const nid=p=>p+(RID++);
  let DAY=1; /* which day of Earth week the seedling shows */
  function lin(p,a,b,x2=0,y2=1){return `<linearGradient id="${p}" x1="0" y1="0" x2="${x2}" y2="${y2}"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient>`}
  const sh=(id,s,o=.5)=>`<filter id="${id}" x="-40%" y="-40%" width="180%" height="180%"><feDropShadow dx="0" dy="${f1(s*.6)}" stdDeviation="${s}" flood-color="#000" flood-opacity="${o}"/></filter>`;
  function leafPath(len,wid,kind='oval'){const L=len,W=wid/2;
   if(kind==='heart')return `M0 0C${f1(L*.1)} ${f1(-W*1.5)} ${f1(L*.75)} ${f1(-W*1.1)} ${L} 0C${f1(L*.75)} ${f1(W*1.1)} ${f1(L*.1)} ${f1(W*1.5)} 0 0Z`;
   return `M0 0C${f1(L*.3)} ${f1(-W)} ${f1(L*.7)} ${f1(-W)} ${L} 0C${f1(L*.7)} ${f1(W)} ${f1(L*.3)} ${f1(W)} 0 0Z`}
  function leaf(x,y,ang,len,wid,c1,c2,o={}){const p=nid('lf');
   let s=`<defs>${lin(p,c1,c2,1,1)}</defs><g transform="translate(${f1(x)} ${f1(y)}) rotate(${f1(ang)})"><path d="${leafPath(len,wid,o.kind)}" fill="url(#${p})" stroke="${dk(c2,.35)}" stroke-width="1"/><path d="M${f1(len*.04)} 0Q${f1(len*.5)} ${f1(-wid*.04)} ${f1(len*.92)} 0" stroke="${lt(c1,.4)}" stroke-width="${f1(Math.max(1,wid*.05))}" fill="none" opacity=".8"/></g>`;return s}
  const GR=['#8ED46A','#3A8634'],GRD=['#5FA848','#1F5422'];

  /* ---------- the room: a dim spring evening, sage walls, warm lamp ---------- */
  function room(W2,H2,win){const id=nid('rm');const fl=H2*.8;
   let s=`<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0C1612"/><stop offset="1" stop-color="#16241C"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2A1D14"/><stop offset="1" stop-color="#0E0A07"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".6"/></radialGradient><radialGradient id="${id}l" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#FFC982" stop-opacity=".3"/><stop offset="1" stop-color="#FFC982" stop-opacity="0"/></radialGradient></defs>`;
   s+=`<rect width="${W2}" height="${H2}" fill="url(#${id}w)"/>`;
   /* soft leaf-print wallpaper, very faint */
   const r=rnd(9);for(let k=0;k<(W2>600?70:18);k++){const x=r()*W2,y=r()*fl*.9;s+=`<path d="${leafPath(26,12)}" transform="translate(${f1(x)} ${f1(y)}) rotate(${f1(r()*360)})" fill="#9FD08A" opacity=".035"/>`}
   s+=`<rect y="${f1(fl-H2*.16)}" width="${W2}" height="${f1(H2*.16)}" fill="#000" opacity=".2"/><rect y="${f1(fl-H2*.16)}" width="${W2}" height="4" fill="#2C4234" opacity=".8"/>`;
   if(win)s+=windowSVG(win);
   s+=`<rect y="${fl}" width="${W2}" height="${H2-fl}" fill="url(#${id}f)"/>`;for(let k=1;k<6;k++)s+=`<path d="M0 ${f1(fl+(H2-fl)*k/6)} H${W2}" stroke="#000" stroke-opacity=".25" stroke-width="2"/>`;
   s+=`<rect y="${fl-10}" width="${W2}" height="10" fill="#0E1712"/>`;
   return {bg:s,vig:`<rect width="${W2}" height="${H2}" fill="url(#${id}v)"/>`,lamp:(x,y,R)=>`<circle class="lampg" cx="${x}" cy="${y}" r="${R}" fill="url(#${id}l)"/>`}}

  /* ---------- window: spring dusk, a blossom tree, people passing on the path ---------- */
  function groundY(){return 0}
  function windowSVG([wx,wy,ww,wh]){const id=nid('wn');const r=rnd(77);const gy=wh-46;
   let s=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2B3F78"/><stop offset=".55" stop-color="#C88AA0"/><stop offset="1" stop-color="#F6B98A"/></linearGradient><clipPath id="${id}c"><rect x="0" y="0" width="${ww}" height="${wh}"/></clipPath><radialGradient id="${id}ff"><stop offset="0" stop-color="#F6FF9A"/><stop offset="1" stop-color="#F6FF9A" stop-opacity="0"/></radialGradient></defs><g transform="translate(${wx} ${wy})"><g clip-path="url(#${id}c)">`;
   s+=`<rect width="${ww}" height="${wh}" fill="url(#${id}s)"/>`;
   for(let k=0;k<14;k++)s+=`<circle cx="${f1(r()*ww)}" cy="${f1(r()*wh*.3)}" r="${f1(.8+r()*1.2)}" fill="#fff" opacity="${f1(.4+r()*.5)}"/>`;
   s+=`<circle cx="${f1(ww*.7)}" cy="${f1(wh*.62)}" r="34" fill="#FFD9A0" opacity=".85"/>`;
   /* far hills and the grassy bank */
   s+=`<path d="M0 ${f1(wh*.62)}C${f1(ww*.3)} ${f1(wh*.52)} ${f1(ww*.6)} ${f1(wh*.66)} ${ww} ${f1(wh*.56)}V${wh}H0Z" fill="#4E6E7E"/><path d="M0 ${f1(wh*.72)}C${f1(ww*.4)} ${f1(wh*.64)} ${f1(ww*.7)} ${f1(wh*.76)} ${ww} ${f1(wh*.7)}V${wh}H0Z" fill="#3E6A44"/>`;
   /* blossom tree on the left, behind the path */
   const tx=ww*.24,ty=gy-6;s+=`<path d="M${f1(tx-8)} ${ty}C${f1(tx-6)} ${f1(ty-60)} ${f1(tx-14)} ${f1(ty-100)} ${f1(tx-30)} ${f1(ty-140)}M${f1(tx-4)} ${f1(ty-70)}C${f1(tx+10)} ${f1(ty-100)} ${f1(tx+30)} ${f1(ty-120)} ${f1(tx+50)} ${f1(ty-140)}M${f1(tx+8)} ${ty}C${f1(tx+6)} ${f1(ty-40)} ${f1(tx+2)} ${f1(ty-60)} ${f1(tx-4)} ${f1(ty-70)}" stroke="#3A2A24" stroke-width="9" fill="none" stroke-linecap="round"/>`;
   for(let k=0;k<160;k++){const a=r()*Math.PI*2,rr=Math.sqrt(r());const x=tx+10+Math.cos(a)*rr*84,y=ty-150+Math.sin(a)*rr*52;s+=`<circle cx="${f1(x)}" cy="${f1(y)}" r="${f1(4+r()*5)}" fill="${['#F7C6D6','#F2A9C2','#FFE2EA','#E58DAE'][k%4]}" opacity=".95"/>`}
   /* path */
   s+=`<rect x="0" y="${gy}" width="${ww}" height="${wh-gy}" fill="#8A7A66"/><rect x="0" y="${gy}" width="${ww}" height="5" fill="#A8977E"/><rect x="0" y="${gy+24}" width="${ww}" height="${wh-gy}" fill="#4E7A46"/>`;
   for(let x=4;x<ww;x+=7)s+=`<path d="M${x} ${gy+26}l2 -9l2 9" fill="#6FA05E"/>`;
   /* tulips along the front of the bank */
   for(let x=12;x<ww;x+=30){const c=['#E8453A','#F6C232','#F07AA0'][Math.round(x/30)%3];s+=`<path d="M${x} ${wh}V${gy+38}" stroke="#3E7A34" stroke-width="2.4"/><path d="M${x-6} ${gy+40}C${x-7} ${gy+28} ${x-3} ${gy+26} ${x} ${gy+30}C${x+3} ${gy+26} ${x+7} ${gy+28} ${x+6} ${gy+40}Q${x} ${gy+44} ${x-6} ${gy+40}Z" fill="${c}"/>`}
   /* birds flying (flap animated) */
   s+=`<g class="birds" data-w="${ww}" data-h="${wh}">${[0,1,2].map(k=>`<path class="bird" data-k="${k}" d="" stroke="#1E2438" stroke-width="2.4" fill="none" stroke-linecap="round"/>`).join('')}</g>`;
   /* walkers on the path */
   s+=`<g class="walkers" data-w="${ww}" data-gy="${gy}">${walkers()}</g>`;
   /* the young tree planted by the path on Earth Day (day 1 the two gardeners are still planting it) */
   s+=planted(ww*.62,gy+12,DAY===1);
   /* fireflies over the grass */
   for(let k=0;k<10;k++)s+=`<circle class="ffly" data-x="${f1(r()*ww)}" data-y="${f1(gy+30+r()*(wh-gy-40))}" data-k="${k}" r="7" fill="url(#${id}ff)"/>`;
   s+=`</g>`;
   /* frame, glazing bars, sill, curtains */
   s+=`<rect x="-14" y="-14" width="${ww+28}" height="${wh+28}" fill="none" stroke="#EDE6D6" stroke-width="16"/><rect x="-20" y="-20" width="${ww+40}" height="${wh+40}" fill="none" stroke="#000" stroke-opacity=".35" stroke-width="2"/>
   <path d="M${ww/2} 0V${wh}M0 ${f1(wh*.42)}H${ww}" stroke="#EDE6D6" stroke-width="9"/>
   <rect x="-34" y="${wh+8}" width="${ww+68}" height="18" rx="4" fill="#E2D9C6"/><rect x="-34" y="${wh+24}" width="${ww+68}" height="6" fill="#000" opacity=".3"/>`;
   /* small herb pots on the sill */
   [[ww*.18,'#C8643B'],[ww*.5,'#D8D0BE'],[ww*.82,'#C8643B']].forEach(([x,c],k)=>{s+=`<path d="M${f1(x-16)} ${wh-14}L${f1(x-12)} ${wh+8}H${f1(x+12)}L${f1(x+16)} ${wh-14}Z" fill="${c}" stroke="${dk(c,.4)}"/>`;for(let j=0;j<7;j++){const a=-150+j*20;s+=leaf(x,wh-14,a,16+(j%3)*5,8,GR[0],GR[1])}});
   s+=`<g class="curtL"><path d="M-30 -40C-20 ${f1(wh*.3)} -40 ${f1(wh*.7)} -36 ${wh+20}H-60V-40Z" fill="#B9C9A6"/><path d="M-46 -40C-42 ${f1(wh*.4)} -48 ${f1(wh*.7)} -52 ${wh+20}" stroke="#8FA27C" stroke-width="3" fill="none"/></g>
   <g class="curtR"><path d="M${ww+44} -40C${ww+30} ${f1(wh*.3)} ${ww+40} ${f1(wh*.7)} ${ww+50} ${wh+20}H${ww+90}V-40Z" fill="#B9C9A6"/><path d="M${ww+60} -40C${ww+54} ${f1(wh*.4)} ${ww+62} ${f1(wh*.7)} ${ww+70} ${wh+20}" stroke="#8FA27C" stroke-width="3" fill="none"/></g>
   <rect x="-66" y="-50" width="${ww+160}" height="10" rx="5" fill="#6A5236"/>`;
   return s+`</g>`}

  /* ---------- people walking past: feet on the path at y=0, facing left, 1.6x size ---------- */
  const skin='#E8B48A',skin2='#A86E48';
  function leg(hx,hy,col,shoe,ph){/* hip at (hx,hy); the leg swings from the hip */return `<g class="lg" data-hx="${hx}" data-hy="${hy}" data-ph="${ph}"><path d="M${hx} ${hy}L${hx} -3" stroke="${col}" stroke-width="7" stroke-linecap="round"/><path d="M${hx+2} -1h-10a3.5 3.5 0 0 1 0 -6h8Z" fill="${shoe}"/></g>`}
  function arm(sx,sy,len,col,ph,hand=skin){return `<g class="arm" data-hx="${sx}" data-hy="${sy}" data-ph="${ph}"><path d="M${sx} ${sy}L${sx} ${sy+len}" stroke="${col}" stroke-width="6.5" stroke-linecap="round"/><circle cx="${sx}" cy="${sy+len+2}" r="3.6" fill="${hand}"/></g>`}
  function face(x,y,r,hair,sk=skin){return `<circle cx="${x}" cy="${y}" r="${r}" fill="${sk}"/><circle cx="${x-r*.45}" cy="${y-r*.1}" r="${r*.13}" fill="#2A1A10"/><path d="M${x-r*.62} ${y+r*.35}q${r*.25} ${r*.2} ${r*.5} 0" stroke="#7A3A20" stroke-width="1.3" fill="none" stroke-linecap="round"/><circle cx="${x-r*.18}" cy="${y+r*.25}" r="${r*.2}" fill="#E07A6A" opacity=".45"/>`+(hair?`<path d="M${x-r} ${y-r*.1}C${x-r} ${y-r*1.3} ${x+r*1.1} ${y-r*1.4} ${x+r} ${y+r*.2}C${x+r*.6} ${y-r*.3} ${x} ${y-r*.6} ${x-r} ${y-r*.1}Z" fill="${hair}"/>`:'')}
  function body(o){const H=o.h||1;const hip=-44*H;let s=leg(-4,hip,o.legs,o.shoe||'#2A1E16',1)+(o.backArm?arm(4,-84*H,34*H,o.coat,0,o.skin):'');
   s+=`<path d="M-13 ${-88*H}h26l4 ${46*H}h-34Z" fill="${o.coat}"/><path d="M0 ${-88*H}v${44*H}" stroke="#000" stroke-opacity=".15" stroke-width="2"/>`+leg(4,hip,o.legs,o.shoe||'#2A1E16',0);
   return s+face(0,-100*H,11,o.hair,o.skin||skin)+(o.hat||'')}
  function wSapling(){/* carrying a young tree in a pot with both arms */
   let s=body({coat:'#C97B3A',legs:'#3A4A6A',hair:'#4A2E1C'});
   s+=`<path d="M-30 -66h24l-3 22h-18Z" fill="#B85E36" stroke="#6A3018"/><path d="M-18 -66C-20 -96 -16 -120 -18 -146" stroke="#6A4A2C" stroke-width="3.4" fill="none"/>`;
   for(let k=0;k<11;k++){const a=-180+k*36,y=-110-(k%4)*10;s+=leaf(-18,y,a,18,10,GR[0],GR[1])}
   s+=`<path d="M6 -84L-10 -60M-2 -84L-24 -58" stroke="#C97B3A" stroke-width="6.5" stroke-linecap="round"/><circle cx="-10" cy="-58" r="3.6" fill="${skin}"/><circle cx="-26" cy="-56" r="3.6" fill="${skin}"/>`;
   return `<g class="wk" data-off="0" data-sp="1">${s}</g>`}
  function wKite(){/* a kid flying a kite: string from the raised hand up to a diamond kite with a bow tail */
   let s=body({h:.72,coat:'#3E8FD2',legs:'#2E3A5A',hair:'#2A1A10',skin:skin2,backArm:true});
   s+=`<path d="M-4 -60L-20 -82" stroke="#3E8FD2" stroke-width="6" stroke-linecap="round"/><circle cx="-21" cy="-84" r="3.4" fill="${skin2}"/>`;
   s+=`<g class="kite" data-ax="-21" data-ay="-84"><path class="kstr" d="M-21 -84Q20 -160 40 -230" stroke="#F4F0E6" stroke-width="1.4" fill="none"/><g class="kbody" transform="translate(40 -230)"><path d="M0 -26L18 0L0 30L-18 0Z" fill="#F2C232"/><path d="M0 -26L18 0L0 0Z" fill="#E8453A"/><path d="M0 0L-18 0L0 30Z" fill="#E8453A"/><path d="M0 -26V30M-18 0H18" stroke="#6A4A1A" stroke-width="1.4"/><path class="ktail" d="M0 30q-8 14 0 26t0 26" stroke="#F4F0E6" stroke-width="1.4" fill="none"/>${[0,1,2].map(k=>`<path class="kbow" data-k="${k}" d="M-5 ${44+k*14}l10 -4v8Z" fill="${['#3E8FD2','#7CD36B','#F07AA0'][k]}"/>`).join('')}</g></g>`;
   return `<g class="wk" data-off=".34" data-sp="1.15">${s}</g>`}
  function wCan(){/* someone carrying a green watering can, swinging gently */
   let s=body({coat:'#6A4AA8',legs:'#3A2A22',hair:'#C9A060',backArm:true});
   s+=`<g class="can" data-ax="2" data-ay="-50"><path d="M2 -84L2 -52" stroke="#6A4AA8" stroke-width="6.5" stroke-linecap="round"/><circle cx="2" cy="-50" r="3.6" fill="${skin}"/><path d="M-6 -50Q2 -60 10 -50" stroke="#2E7A4A" stroke-width="3" fill="none"/><path d="M-10 -46h24l-2 26h-20Z" fill="#3E9A5A" stroke="#1E5A34"/><path d="M-10 -40L-28 -54" stroke="#3E9A5A" stroke-width="4" stroke-linecap="round"/><ellipse cx="-29" cy="-55" rx="3" ry="5" transform="rotate(-40 -29 -55)" fill="#2E7A4A"/></g>`;
   return `<g class="wk" data-off=".66" data-sp=".9">${s}</g>`}
  function wTulips(){/* carrying a pot of red tulips with both arms */
   let s=body({coat:'#C97B3A',legs:'#3A4A6A',hair:'#4A2E1C'});
   s+=`<path d="M-30 -66h24l-3 22h-18Z" fill="#B85E36" stroke="#6A3018"/>`;
   [[-24,-92,-8],[-18,-100,0],[-12,-90,8]].forEach(([x,y,a])=>{s+=`<path d="M-18 -66Q${x} -78 ${x} ${y}" stroke="#3E7A34" stroke-width="2.4" fill="none"/><path d="M${x-6} ${y+2}C${x-7} ${y-10} ${x-3} ${y-12} ${x} ${y-8}C${x+3} ${y-12} ${x+7} ${y-10} ${x+6} ${y+2}Q${x} ${y+6} ${x-6} ${y+2}Z" fill="#E8453A" transform="rotate(${a} ${x} ${y})"/>`});
   s+=leaf(-18,-70,-150,16,8,GR[0],GR[1])+leaf(-18,-70,-30,16,8,GR[0],GR[1]);
   s+=`<path d="M6 -84L-10 -60M-2 -84L-24 -58" stroke="#C97B3A" stroke-width="6.5" stroke-linecap="round"/><circle cx="-10" cy="-58" r="3.6" fill="${skin}"/><circle cx="-26" cy="-56" r="3.6" fill="${skin}"/>`;
   return `<g class="wk" data-off="0">${s}</g>`}
  /* Earth Day itself (day 1) the sapling carrier and the watering can walker are planting the tree, so only the kite
     flyer walks past; the rest of the week all three walk past the tree (the carrier now has a pot of tulips) */
  function walkers(){return DAY===1?wKite():wTulips()+wKite()+wCan()}
  /* the young tree by the path, tied to a stake; feet on y = 0, the same 1.6x size as the walkers */
  function plantedTree(){let s=`<ellipse cx="0" cy="0" rx="20" ry="5" fill="#4A3020"/><ellipse cx="-2" cy="-1.5" rx="13" ry="3.2" fill="#6E4A2E"/>`;
   s+=`<path d="M11 2L14 -50" stroke="#B89060" stroke-width="2.6" stroke-linecap="round"/><path d="M1 -34L13.2 -36M1 -22L12.6 -24" stroke="#D8B77E" stroke-width="1.6"/>`;
   s+=`<g class="tree"><path d="M0 0C-2 -26 2 -52 0 -80" stroke="#6A4A2C" stroke-width="3.4" fill="none"/>`;
   for(let k=0;k<11;k++){const a=-180+k*36,y=-44-(k%4)*10;s+=leaf(0,y,a,18,10,GR[0],GR[1])}
   return s+`</g>`}
  /* kneeling by the tree, patting the soil down (faces left, knee on the ground) */
  function wKneel(){const coat='#C97B3A',legs='#3A4A6A',shoe='#2A1E16';
   let s=`<path d="M6 -30L8 -4L24 -4" stroke="${legs}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" fill="none"/><path d="M24 -1h6a3 3 0 0 0 0 -6h-6Z" fill="${shoe}"/>`;
   s+=`<path d="M6 -30L-10 -27L-10 -3" stroke="${dk(legs,.15)}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" fill="none"/><path d="M-9 -1h-9a3.5 3.5 0 0 1 0 -6h9Z" fill="${shoe}"/>`;
   s+=`<path class="pat" data-k="1" d="" stroke="${dk(coat,.12)}" stroke-width="6.5" stroke-linecap="round" fill="none"/>`;
   s+=`<g transform="rotate(-55 6 -30)"><path d="M-7 -74h26l4 44h-34Z" fill="${coat}"/><path d="M6 -74v44" stroke="#000" stroke-opacity=".15" stroke-width="2"/>${face(6,-86,11,'#4A2E1C')}</g>`;
   s+=`<path class="pat" data-k="0" d="" stroke="${coat}" stroke-width="6.5" stroke-linecap="round" fill="none"/>`;
   return s+[0,1].map(k=>`<circle class="hand" data-k="${k}" r="3.6" fill="${skin}"/>`).join('')}
  /* watering the new tree: faces right (drawn mirrored), the can tipped so water falls on the soil */
  function wWater(){let s=body({coat:'#6A4AA8',legs:'#3A2A22',hair:'#C9A060',backArm:true});
   s+=`<path d="M-2 -84L-18 -64" stroke="#6A4AA8" stroke-width="6.5" stroke-linecap="round"/><circle cx="-19" cy="-62" r="3.6" fill="${skin}"/>`;
   s+=`<g class="tip"><path d="M-27 -66Q-19 -76 -11 -66" stroke="#2E7A4A" stroke-width="3" fill="none"/><path d="M-31 -62h24l-2 26h-20Z" fill="#3E9A5A" stroke="#1E5A34"/><path d="M-30 -56L-48 -66" stroke="#3E9A5A" stroke-width="4" stroke-linecap="round"/><ellipse cx="-49" cy="-67" rx="3" ry="5" transform="rotate(-40 -49 -67)" fill="#2E7A4A"/></g>`;
   return s}
  function planted(x,y,planting){let s=`<g class="planted" data-x="${f1(x)}" data-y="${f1(y)}"><g transform="translate(${f1(x)} ${f1(y)}) scale(1.6)">${plantedTree()}</g>`;
   if(planting){s+=`<g class="waterer" transform="translate(${f1(x-80)} ${f1(y)}) scale(-1.6 1.6)">${wWater()}</g>`;
    s+=`<g transform="translate(${f1(x+70)} ${f1(y)}) scale(1.6)">${wKneel()}</g>`;
    for(let k=0;k<6;k++)s+=`<ellipse class="drop" data-k="${k}" rx="1.6" ry="3" fill="#BFE6FF" opacity="0"/>`}
   return s+`</g>`}

  /* ---------- the plant corner ---------- */
  function monstera(x,by,sc){/* a big monstera in a woven basket; each leaf has its splits cut with a mask */
   const id=nid('ms');let s=`<defs>${lin(id+'b','#C9A46A','#7A5A30',1,1)}${sh(id+'s',6,.5)}</defs><g transform="translate(${x} ${by}) scale(${sc})">`;
   const L=[[-60,-250,-150,1],[-30,-300,-110,1.15],[20,-280,-70,1.05],[60,-210,-30,.95],[-90,-180,-170,.85],[40,-330,-90,1]];
   L.forEach(([lx,ly,ang,sz],k)=>{const m=nid('mm');const len=150*sz,w=120*sz;
    let slits='';for(let j=0;j<5;j++){const t=.22+j*.15;slits+=`<path d="M${f1(len*t)} ${f1(-w*.52)}L${f1(len*(t+.04))} ${f1(-w*.12)}L${f1(len*(t+.07))} ${f1(-w*.52)}Z M${f1(len*t)} ${f1(w*.52)}L${f1(len*(t+.04))} ${f1(w*.12)}L${f1(len*(t+.07))} ${f1(w*.52)}Z" fill="#000"/>`;if(j>0&&j<4)slits+=`<ellipse cx="${f1(len*(t+.02))}" cy="${f1((j%2?-1:1)*w*.24)}" rx="${f1(len*.03)}" ry="${f1(w*.05)}" fill="#000"/>`}
    const shape=`M0 0C${f1(len*.05)} ${f1(-w*.75)} ${f1(len*.8)} ${f1(-w*.62)} ${len} 0C${f1(len*.8)} ${f1(w*.62)} ${f1(len*.05)} ${f1(w*.75)} 0 0Z`;
    s+=`<path d="M0 -40C${f1(lx*.4)} ${f1(ly*.5)} ${f1(lx*.8)} ${f1(ly*.8)} ${lx} ${ly}" stroke="#3E7A30" stroke-width="5" fill="none"/>`;
    s+=`<g class="sway" data-ax="${x+lx*sc}" data-ay="${by+ly*sc}" data-k="${k}"><defs><mask id="${m}" maskUnits="userSpaceOnUse" x="-20" y="-200" width="${f1(len+40)}" height="400"><path d="${shape}" fill="#fff"/>${slits}</mask>${lin(m+'g','#5FB04A','#1F5E26',1,1)}</defs><g transform="translate(${lx} ${ly}) rotate(${ang})"><g mask="url(#${m})"><path d="${shape}" fill="url(#${m}g)"/><path d="M0 0L${len} 0" stroke="#A8DC86" stroke-width="2.4" opacity=".8"/>${[.25,.4,.55,.7].map(t=>`<path d="M${f1(len*t*.8)} 0L${f1(len*(t+.1))} ${f1(-w*.4)}M${f1(len*t*.8)} 0L${f1(len*(t+.1))} ${f1(w*.4)}" stroke="#A8DC86" stroke-width="1.2" opacity=".55"/>`).join('')}<path d="M${f1(len*.15)} ${f1(-w*.3)}Q${f1(len*.45)} ${f1(-w*.46)} ${f1(len*.75)} ${f1(-w*.24)}" stroke="#fff" stroke-width="5" fill="none" opacity=".18" stroke-linecap="round"/></g></g></g>`});
   /* basket */
   s+=`<g filter="url(#${id}s)"><path d="M-80 -60H80L64 0H-64Z" fill="url(#${id}b)"/>`;for(let j=0;j<5;j++)s+=`<path d="M${-78+j*1.6} ${-50+j*11}H${78-j*1.6*2}" stroke="#5A3E1E" stroke-width="2" opacity=".6"/>`;
   for(let j=-7;j<=7;j++)s+=`<path d="M${j*10} -60L${j*8.4} 0" stroke="#E2C48C" stroke-width="2" opacity=".5"/>`;
   s+=`<rect x="-84" y="-66" width="168" height="10" rx="5" fill="#B08850"/></g></g>`;return s}
  function pothos(x,top,len){/* a hanging pothos in a jute hanger; trailing vines sway */
   let s=`<path d="M${x} 0V${top-40}" stroke="#D8B77E" stroke-width="3"/><path d="M${x} ${top-40}L${x-34} ${top}M${x} ${top-40}L${x+34} ${top}M${x} ${top-40}L${x} ${top}" stroke="#D8B77E" stroke-width="2.4"/>`;
   s+=`<path d="M${x-36} ${top}H${x+36}L${x+28} ${top+44}H${x-28}Z" fill="#E8E0CE" stroke="#9A8E76"/><path d="M${x-34} ${top+10}H${x+34}" stroke="#C8BEA6" stroke-width="3"/>`;
   [[-30,len*.9],[-10,len],[14,len*.75],[32,len*.6],[0,len*.45]].forEach(([dx,L],k)=>{let v=`<g class="vine" data-ax="${x+dx}" data-ay="${top+20}" data-k="${k}"><path d="M${x+dx} ${top+20}C${x+dx-10} ${top+L*.3} ${x+dx+12} ${top+L*.6} ${x+dx} ${top+L}" stroke="#4E8A30" stroke-width="2.4" fill="none"/>`;
    for(let i=0;i<9;i++){const t=(i+.5)/9,yy=top+20+t*(L-20),xx=x+dx+Math.sin(t*6)*8;v+=leaf(xx,yy,i%2?20:160,22-t*6,16-t*4,'#B6D86A','#3E8A34',{kind:'heart'})}
    s+=v+`</g>`});
   for(let k=0;k<8;k++)s+=leaf(x-30+k*8,top+4,-170+k*22,24,17,'#B6D86A','#3E8A34',{kind:'heart'});
   return s}
  function sideTable(x,top,legY){const id=nid('tb');return `<defs>${lin(id+'w','#7A4E2C','#3A2414',1,0)}${lin(id+'t','#9A6638','#5A3618')}</defs><path d="M${x-120} ${top+14}L${x-138} ${legY}M${x+120} ${top+14}L${x+138} ${legY}M${x} ${top+20}L${x} ${legY+6}" stroke="url(#${id}w)" stroke-width="14" stroke-linecap="round"/><ellipse cx="${x}" cy="${legY+10}" rx="170" ry="16" fill="#000" opacity=".35"/><ellipse cx="${x}" cy="${top+8}" rx="160" ry="24" fill="#4A2C16"/><ellipse cx="${x}" cy="${top}" rx="160" ry="24" fill="url(#${id}t)"/><ellipse cx="${x-30}" cy="${top-4}" rx="90" ry="9" fill="#fff" opacity=".07"/>`}
  /* desk globe: turning Earth on a tilted brass half meridian and a wooden base */
  const LAND=[ /* continents laid out on a flat strip; x in globe radii (0..4 is one full turn), y in radii */
   `M.3 -.7C.6 -.82 1.0 -.78 1.08 -.6C1.12 -.48 .98 -.42 .92 -.3C.86 -.2 .78 -.14 .7 -.12C.62 -.1 .56 -.2 .48 -.26C.36 -.34 .2 -.46 .3 -.7Z`,
   `M.78 -.06C.9 -.04 1.04 0 1.1 .08C1.18 .18 1.14 .3 1.06 .42C1 .54 .94 .7 .86 .78C.82 .62 .8 .48 .76 .34C.72 .2 .7 .06 .78 -.06Z`,
   `M1.72 -.66C1.86 -.74 2.02 -.7 2.04 -.58C2.06 -.48 1.94 -.44 1.9 -.36C1.98 -.3 2.1 -.28 2.14 -.14C2.18 .02 2.1 .2 2.0 .36C1.94 .48 1.88 .56 1.84 .5C1.8 .38 1.76 .24 1.7 .1C1.64 -.04 1.6 -.16 1.66 -.3C1.62 -.4 1.62 -.56 1.72 -.66Z`,
   `M2.1 -.74C2.4 -.84 2.9 -.8 3.1 -.62C3.2 -.5 3.1 -.36 2.96 -.3C2.86 -.18 2.78 -.06 2.66 -.04C2.56 -.02 2.5 -.14 2.42 -.2C2.3 -.3 2.18 -.36 2.14 -.48C2.1 -.6 2.04 -.68 2.1 -.74Z`,
   `M3.1 .3C3.24 .24 3.42 .28 3.46 .38C3.5 .48 3.4 .56 3.28 .56C3.16 .56 3.06 .48 3.1 .3Z`];
  function globe(cx,cy,r,o={}){const id=nid('gb');let land='';for(let rep=0;rep<2;rep++)LAND.forEach(d=>land+=`<path d="${d}" transform="translate(${f1(rep*4*r)} 0) scale(${r})" fill="#6CC04E" stroke="#2E7A2C" stroke-width="${f1(1/r)}"/>`);
   let s=`<defs><clipPath id="${id}c"><circle cx="0" cy="0" r="${r}"/></clipPath><radialGradient id="${id}o" cx=".35" cy=".35" r=".8"><stop offset="0" stop-color="#7CC8FF"/><stop offset=".6" stop-color="#2F7FD0"/><stop offset="1" stop-color="#103A70"/></radialGradient><radialGradient id="${id}d" cx=".34" cy=".32" r=".85"><stop offset=".45" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".55"/></radialGradient>${lin(id+'br','#FFE7A0','#9A6E1C',1,1)}${lin(id+'wd','#8A5A30','#3E2410')}</defs>`;
   /* base and neck */
   s+=`<ellipse cx="${cx}" cy="${cy+r+58}" rx="${f1(r*.75)}" ry="${f1(r*.16)}" fill="url(#${id}wd)"/><rect x="${f1(cx-r*.75)}" y="${f1(cy+r+48)}" width="${f1(r*1.5)}" height="10" rx="5" fill="url(#${id}wd)"/><path d="M${cx} ${cy+r+48}C${cx} ${f1(cy+r+20)} ${f1(cx+(r+10)*.38)} ${f1(cy+(r+10)*1.05)} ${f1(cx+(r+10)*.397)} ${f1(cy+(r+10)*.917)}" stroke="url(#${id}br)" stroke-width="8" fill="none" stroke-linecap="round"/>`;
   s+=`<g transform="translate(${cx} ${cy}) rotate(-23.4)"><circle r="${r}" fill="url(#${id}o)"/><g clip-path="url(#${id}c)"><g class="spin" data-p="${f1(4*r)}" data-v="${o.v||14}" transform="translate(${f1(-2*r)} 0)">${land}</g><ellipse cx="0" cy="${f1(-r*.92)}" rx="${f1(r*.5)}" ry="${f1(r*.16)}" fill="#F4FAFF"/><ellipse cx="0" cy="${f1(r*.94)}" rx="${f1(r*.6)}" ry="${f1(r*.14)}" fill="#F4FAFF"/></g>
   <circle r="${r}" fill="url(#${id}d)"/><ellipse cx="${f1(-r*.38)}" cy="${f1(-r*.42)}" rx="${f1(r*.26)}" ry="${f1(r*.13)}" transform="rotate(-35 ${f1(-r*.38)} ${f1(-r*.42)})" fill="#fff" opacity=".45"/>
   <path d="M0 ${f1(-r-14)}A${r+14} ${r+14} 0 0 1 0 ${f1(r+14)}" stroke="url(#${id}br)" stroke-width="8" fill="none"/><circle cx="0" cy="${f1(-r-10)}" r="5" fill="#E8C060"/><circle cx="0" cy="${f1(r+10)}" r="5" fill="#E8C060"/></g>`;
   return s}
  /* the seedling that grows through Earth week: day 1 a sprout, day 7 in flower */
  function seedling(x,by,day){const k=(day-1)/6;let s=`<path d="M${x-30} ${by-56}H${x+30}L${x+24} ${by}H${x-24}Z" fill="#C8643B" stroke="#6A3018"/><rect x="${x-34}" y="${by-62}" width="68" height="14" rx="3" fill="#D4733F" stroke="#6A3018"/><ellipse cx="${x}" cy="${by-60}" rx="28" ry="4" fill="#3A2416"/><path d="M${x-14} ${by-34}Q${x} ${by-40} ${x+14} ${by-34}" stroke="#F2A273" opacity=".4" stroke-width="2" fill="none"/>`;
   const h=24+k*110;s+=`<path d="M${x} ${by-60}C${x-4} ${by-60-h*.4} ${x+5} ${by-60-h*.7} ${x} ${by-60-h}" stroke="#3E8C34" stroke-width="${f1(3+k*2)}" fill="none" stroke-linecap="round"/>`;
   const n=1+Math.round(k*3);for(let i=0;i<n;i++){const t=n===1?1:(i+1)/(n+.3);const y=by-60-h*t;const sz=(16+k*14)*(1-t*.25);s+=leaf(x,y,-160+(i%2)*8,sz*1.4,sz,GR[0],GR[1],{kind:'heart'})+leaf(x,y,-20-(i%2)*8,sz*1.5,sz*1.05,GR[0],GR[1],{kind:'heart'})}
   if(day>=5){const fy=by-60-h-(day>=7?24:6),R=day>=7?34:10;if(day>=7){for(let q=0;q<14;q++){const a=q/14*Math.PI*2;s+=`<ellipse cx="${f1(x+Math.cos(a)*R*.6)}" cy="${f1(fy+Math.sin(a)*R*.6)}" rx="${f1(R*.42)}" ry="${f1(R*.15)}" transform="rotate(${f1(a*180/Math.PI)} ${f1(x+Math.cos(a)*R*.6)} ${f1(fy+Math.sin(a)*R*.6)})" fill="${q%2?'#FFD84A':'#F6C232'}" stroke="#B88A10"/>`}s+=`<circle cx="${x}" cy="${fy}" r="${f1(R*.34)}" fill="#5A3410"/>`;for(let q=0;q<16;q++){const a=q/16*Math.PI*2;s+=`<circle cx="${f1(x+Math.cos(a)*R*.2)}" cy="${f1(fy+Math.sin(a)*R*.2)}" r="1.6" fill="#9A6A2A"/>`}}else s+=`<ellipse cx="${x}" cy="${fy}" rx="6" ry="10" fill="#9ACD5A" stroke="#3E7A30"/><path d="M${x} ${fy-10}v-4" stroke="#F6C232" stroke-width="3"/>`}
   return s}
  function wateringCan(x,by){return `<ellipse cx="${x}" cy="${by+2}" rx="60" ry="8" fill="#000" opacity=".35"/><path d="M${x-40} ${by}V${by-56}Q${x-40} ${by-64} ${x-30} ${by-64}H${x+22}Q${x+32} ${by-64} ${x+32} ${by-56}V${by}Z" fill="#3E8A5A" stroke="#1E4E30" stroke-width="1.4"/><path d="M${x-30} ${by-64}Q${x-6} ${by-104} ${x+22} ${by-64}" stroke="#2E6A44" stroke-width="7" fill="none"/><path d="M${x+30} ${by-20}L${x+76} ${by-70}" stroke="#3E8A5A" stroke-width="9" stroke-linecap="round"/><ellipse cx="${x+80}" cy="${by-74}" rx="10" ry="6" transform="rotate(-48 ${x+80} ${by-74})" fill="#2E6A44"/><path d="M${x-34} ${by-50}V${by-8}" stroke="#fff" stroke-width="5" opacity=".18" stroke-linecap="round"/>`}
  function plantCorner(sc=1){let s=`<g transform="scale(${sc})">`;
   s+=monstera(-210,140,1);
   s+=sideTable(60,0,140);
   s+=globe(0,-150,82)+seedling(110,4,DAY);
   s+=wateringCan(270,150);
   return s+`</g>`}
  function closePlant(day){const k=DAY;DAY=day;const s=seedling(0,0,day);DAY=k;return s}
  /** The two shapes: wide (TVs, computers) and tall (phones) */
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1622, 130, 250, 390], lamp: [380, 760, 420], pothos: [120, 250, 330], corner: [420, 800, 1] },
    tall: { w: 390, h: 844, win: null, lamp: [170, 700, 220], pothos: null, corner: [200, 760, 0.6] },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t) } which poses everything at t seconds.
   * opts: { layout: "wide"|"tall", day: 1 to 7 (default today), align: "xMidYMax slice" }
   */
  function build(svg, opts) {
    const L = LAYOUTS[(opts && opts.layout) || "wide"];
    DAY = Math.max(1, Math.min(7, (opts && opts.day) || day(new Date()) || 1));
    RID = 0;
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", (opts && opts.align) || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win);
    svg.innerHTML = rm.bg + rm.lamp(...L.lamp) + (L.pothos ? pothos(...L.pothos) : "") +
      `<g transform="translate(${L.corner[0]} ${L.corner[1]})">${plantCorner(L.corner[2])}</g>` + rm.vig;
    return pose(svg);
  }

  /* gathers every moving part, and returns at(t) */
  function pose(root) {
    const q = (s) => [...root.querySelectorAll(s)];
    const spins = q(".spin"), sways = q(".sway"), vines = q(".vine"), lamps = q(".lampg");
    const curtL = q(".curtL"), curtR = q(".curtR"), flies = q(".ffly");
    const walkers = q(".walkers").map((W) => ({ ww: +W.dataset.w, gy: +W.dataset.gy, wk: [...W.querySelectorAll(":scope > .wk")] }));
    const birds = q(".birds").map((B) => ({ ww: +B.dataset.w, wh: +B.dataset.h, b: [...B.querySelectorAll(".bird")] }));
    const trees = q(".tree"), pats = q(".pat"), hands = q(".hand"), tips = q(".tip"), drops = q(".drop");
    const plantedAt = q(".planted").map((p) => ({ x: +p.dataset.x, y: +p.dataset.y }))[0];
    const s07 = wq(0.7), s06 = wq(0.6), s05 = wq(0.5), s11 = wq(1.1), s52 = wq(5.2), s13 = wq(1.3), s19 = wq(1.9);
    const s16 = wq(1.6), s3 = wq(3), s09 = wq(0.9), s14 = wq(1.4), s9 = wq(9), s1 = wq(1), s08 = wq(0.8), s6 = wq(6);
    function at(t) {
      // the desk globe turns once a loop
      spins.forEach((g) => { const P = +g.dataset.p; g.setAttribute("transform", `translate(${f1(-P - ((t * P / LOOP) % P))} 0)`); });
      sways.forEach((g) => { const k = +g.dataset.k; g.setAttribute("transform", `rotate(${f1(Math.sin(t * s07 + k * 1.3) * 1.6)} ${g.dataset.ax} ${g.dataset.ay})`); });
      vines.forEach((g) => { const k = +g.dataset.k; g.setAttribute("transform", `rotate(${f1(Math.sin(t * s06 + k) * 2.4)} ${g.dataset.ax} ${g.dataset.ay})`); });
      curtL.forEach((g) => g.setAttribute("transform", `skewX(${f1(Math.sin(t * s05) * 1.6)})`));
      curtR.forEach((g) => g.setAttribute("transform", `skewX(${f1(Math.sin(t * s05 + 1) * -1.6)})`));
      lamps.forEach((g) => g.setAttribute("opacity", f1(0.9 + 0.1 * Math.sin(t * s11))));
      walkers.forEach(({ ww, gy, wk }) => {
        const span = ww + 260;
        wk.forEach((g) => {
          // one walk past the window per loop, each walker a third of a loop apart
          const off = +g.dataset.off; const cyc = (t / LOOP + off) % 1; const x = ww + 120 - cyc * span; const ph = t * s52;
          g.setAttribute("transform", `translate(${f1(x)} ${f1(gy + 2 - Math.abs(Math.sin(ph)) * 2.2)}) scale(1.6)`);
          g.querySelectorAll(".lg").forEach((l) => { const a = Math.sin(ph + (+l.dataset.ph) * Math.PI) * 24; l.setAttribute("transform", `rotate(${f1(a)} ${l.dataset.hx} ${l.dataset.hy})`); });
          g.querySelectorAll(".arm").forEach((l) => { const a = Math.sin(ph + (+l.dataset.ph) * Math.PI) * -18; l.setAttribute("transform", `rotate(${f1(a)} ${l.dataset.hx} ${l.dataset.hy})`); });
          g.querySelectorAll(".can").forEach((l) => l.setAttribute("transform", `rotate(${f1(Math.sin(ph) * 10)} 2 -84)`));
          g.querySelectorAll(".kbody").forEach((l) => l.setAttribute("transform", `translate(${f1(40 + Math.sin(t * s13) * 6)} ${f1(-230 + Math.sin(t * s19) * 5)}) rotate(${f1(Math.sin(t * s16) * 8)})`));
          g.querySelectorAll(".kstr").forEach((l) => l.setAttribute("d", `M-21 -84Q${f1(20 + Math.sin(t * s11) * 6)} -160 ${f1(40 + Math.sin(t * s13) * 6)} ${f1(-230 + Math.sin(t * s19) * 5)}`));
          g.querySelectorAll(".ktail").forEach((l) => l.setAttribute("d", `M0 30q${f1(-8 + Math.sin(t * s3) * 5)} 14 0 26t0 26`));
        });
      });
      birds.forEach(({ ww, wh, b }) => b.forEach((p) => {
        const k = +p.dataset.k; const cyc = (t * 2 / LOOP + k * 0.13) % 1; const x = -30 + cyc * (ww + 60) + k * 18, y = wh * 0.18 + k * 14 + Math.sin(t * s1 + k) * 4; const f = Math.sin(t * s9 + k * 2) * 7;
        p.setAttribute("d", `M${f1(x - 10)} ${f1(y - f)}Q${f1(x - 5)} ${f1(y - 3)} ${f1(x)} ${f1(y)}Q${f1(x + 5)} ${f1(y - 3)} ${f1(x + 10)} ${f1(y - f)}`);
      }));
      flies.forEach((c) => { const k = +c.dataset.k; c.setAttribute("cx", f1(+c.dataset.x + Math.sin(t * s06 + k) * 12)); c.setAttribute("cy", f1(+c.dataset.y + Math.sin(t * s09 + k * 2) * 6)); c.setAttribute("opacity", f1(Math.max(0, Math.sin(t * s14 + k * 1.7)))); });
      // the young tree by the path moves a little in the breeze
      trees.forEach((g) => g.setAttribute("transform", `rotate(${f1(Math.sin(t * s08) * 2.5)} 0 0)`));
      // Earth Day: the kneeling gardener pats the soil down with both hands, the other waters the tree
      const pk = [[-34, -6], [-44, -5]], sh = [[-30, -48], [-34, -46]];
      pats.forEach((p) => { const k = +p.dataset.k; const [hx2, hy] = pk[k]; const y = hy - Math.abs(Math.sin(t * s6 + k * 1.6)) * 9; p.setAttribute("d", `M${sh[k][0]} ${sh[k][1]}Q${hx2 + 6} ${f1((sh[k][1] + y) / 2 - 4)} ${hx2} ${f1(y)}`); });
      hands.forEach((h) => { const k = +h.dataset.k; const [hx2, hy] = pk[k]; h.setAttribute("cx", hx2); h.setAttribute("cy", f1(hy - Math.abs(Math.sin(t * s6 + k * 1.6)) * 9)); });
      tips.forEach((g) => g.setAttribute("transform", `rotate(${f1(-22 + Math.sin(t * s1) * 4)} -19 -62)`));
      if (plantedAt) {
        // water falls from the spout onto the soil around the tree
        drops.forEach((d) => {
          const k = +d.dataset.k; const a = (t / 0.75 + k / 6) % 1;
          const x0 = plantedAt.x - 80 + 1.6 * 48.7, y0 = plantedAt.y - 1.6 * 55.4, y1 = plantedAt.y - 4;
          d.setAttribute("cx", f1(x0 + (k % 3 - 1) * 3 - a * 6)); d.setAttribute("cy", f1(y0 + (y1 - y0) * a * a));
          d.setAttribute("opacity", f1(Math.sin(a * Math.PI) * 0.9));
        });
      }
    }
    return { at };
  }

  /** Plays a built room live until the element leaves the page */
  function play(svg, scene) {
    const start0 = performance.now() - 4000;
    const tick = (now) => { if (!svg.isConnected) return; scene.at((now - start0) / 1000); requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
  }

  G.BenflixEarth = { LOOP, season, day, start, greeting, line, build, play, LAYOUTS };
})(typeof window !== "undefined" ? window : globalThis);
