/* Benflix Star Wars Day: the Who's watching room (approved 2026-11-17, thread "Design a Star Wars Day theme for Benflix").
 *
 * A domed desert home at twin-sun sunset, all our own drawings. Bottom-left: a laser sword resting on its
 * hooks on a wooden crate (its blade is the colour of the side the profile you're on picked: blue for the
 * light side, red for the dark side) and a flickering blue hologram globe on its projector table. Through the
 * arched window on the right: two suns setting over the dunes, a little ship crossing the sky, and passers-by
 * about every 9 seconds: a hooded traveller with a walking staff, an open-top hover car gliding over the sand,
 * and a rider on a big desert lizard. 4 May only.
 *
 * Like feast.js, the same drawing is used live on the web and by tools/starwars/record.js, which records it as
 * looping videos for the TV and phone app (recorded with the hilt only: the app draws the blade on top, so it
 * can match each profile's side). Everything is a function of the time t (seconds) and repeats every LOOP
 * seconds, so a video of one loop joins up with no jump. The drawing code comes straight from the approved
 * design sheet (starwars-4-whos-watching.html).
 */
(function (G) {
  "use strict";
  const LOOP = 27;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;

  // ---------- dates ----------
  /** 4 May only */
  const season = (d) => d.getMonth() === 4 && d.getDate() === 4;
  const greeting = () => "May the 4th be with you";
  /** blade colours: light side blue, dark side red */
  const BLADES = { light: "#4FB8FF", dark: "#FF2A2A" };

  // ---------- drawing kit (from the approved design sheet) ----------
  let UID=0;const u=p=>p+(UID++)+'_';
  const f1=v=>(+v).toFixed(1);
  const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
  function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
  function rg(p,c,cx=.35,cy=.3,r=.85){return `<radialGradient id="${p}" cx="${cx}" cy="${cy}" r="${r}"><stop offset="0" stop-color="${c[0]}"/><stop offset=".5" stop-color="${c[1]}"/><stop offset="1" stop-color="${c[2]}"/></radialGradient>`}
  function lg(p,c,x2=1,y2=0){return `<linearGradient id="${p}" x1="0" y1="0" x2="${x2}" y2="${y2}">${c.map((s,i)=>`<stop offset="${f1(i/(c.length-1))}" stop-color="${s}"/>`).join('')}</linearGradient>`}
  const glint=(x,y,s,op=1)=>`<path d="M${f1(x)} ${f1(y-s)}Q${f1(x+s*.14)} ${f1(y-s*.14)} ${f1(x+s)} ${f1(y)}Q${f1(x+s*.14)} ${f1(y+s*.14)} ${f1(x)} ${f1(y+s)}Q${f1(x-s*.14)} ${f1(y+s*.14)} ${f1(x-s)} ${f1(y)}Q${f1(x-s*.14)} ${f1(y-s*.14)} ${f1(x)} ${f1(y-s)}Z" fill="#fff" opacity="${op}"/>`;
  const sh=(id,s)=>`<filter id="${id}" x="-40%" y="-40%" width="180%" height="180%"><feDropShadow dx="0" dy="${f1(s*.6)}" stdDeviation="${s}" flood-color="#000" flood-opacity=".5"/></filter>`;
  function fighter(cx,cy,L,rot){const p=u('fi');let s=`<defs>${lg(p+'h',['#5E6878','#F4F7FB','#C9D2DE','#6E7888'],1,0)}${rg(p+'c',['#BFE9FF','#2E6FC0','#0A1A3A'],.35,.3)}${sh(p+'s',3.5)}</defs><g transform="translate(${cx} ${cy}) rotate(${rot})" filter="url(#${p}s)">`;
   const W=L*.5;
   s+=`<path d="M0 ${f1(-L*.5)}L${f1(W)} ${f1(L*.32)}L${f1(W*.46)} ${f1(L*.24)}L${f1(W*.3)} ${f1(L*.4)}L${f1(-W*.3)} ${f1(L*.4)}L${f1(-W*.46)} ${f1(L*.24)}L${f1(-W)} ${f1(L*.32)}Z" fill="url(#${p}h)" stroke="#2A303A" stroke-width="1.4" stroke-linejoin="round"/>`;
   s+=`<path d="M${f1(W*.18)} ${f1(-L*.12)}L${f1(W*.82)} ${f1(L*.26)}L${f1(W*.66)} ${f1(L*.27)}L${f1(W*.12)} ${f1(-L*.02)}Z M${f1(-W*.18)} ${f1(-L*.12)}L${f1(-W*.82)} ${f1(L*.26)}L${f1(-W*.66)} ${f1(L*.27)}L${f1(-W*.12)} ${f1(-L*.02)}Z" fill="#E8742A"/>`;
   s+=`<path d="M0 ${f1(-L*.46)}L${f1(W*.16)} ${f1(L*.36)}H${f1(-W*.16)}Z" fill="#AEB8C6" stroke="#4E5868" stroke-width="1"/>`;
   [-1,1].forEach(sx=>{s+=`<rect x="${f1(sx*W*.22-W*.08)}" y="${f1(L*.36)}" width="${f1(W*.16)}" height="${f1(L*.1)}" rx="2" fill="#3A404C" stroke="#1A1E26"/><ellipse cx="${f1(sx*W*.22)}" cy="${f1(L*.46)}" rx="${f1(W*.07)}" ry="${f1(L*.02)}" fill="#7FD0FF" opacity=".9"/>`});
   s+=`<ellipse cx="0" cy="${f1(-L*.06)}" rx="${f1(W*.12)}" ry="${f1(L*.17)}" fill="url(#${p}c)" stroke="#2A303A" stroke-width="1.2"/><ellipse cx="${f1(-W*.04)}" cy="${f1(-L*.14)}" rx="${f1(W*.04)}" ry="${f1(L*.06)}" fill="#fff" opacity=".8"/>`;
   return s+`</g>`}
  function smooth(P){let d=`M${f1(P[0][0])} ${f1(P[0][1])}`;const n=P.length,g=i=>P[Math.max(0,Math.min(n-1,i))];
   for(let i=0;i<n-1;i++){const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);d+=`C${f1(p1[0]+(p2[0]-p0[0])/6)} ${f1(p1[1]+(p2[1]-p0[1])/6)} ${f1(p2[0]-(p3[0]-p1[0])/6)} ${f1(p2[1]-(p3[1]-p1[1])/6)} ${f1(p2[0])} ${f1(p2[1])}`}return d}
  function saber(x1,y1,x2,y2,col,o={}){const p=u('sb');const L=Math.hypot(x2-x1,y2-y1),a=Math.atan2(y2-y1,x2-x1)*180/Math.PI;const hl=o.hilt??36,bw=o.w??5;
   let s=`<defs><filter id="${p}g" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="${f1(bw*1.4)}"/></filter>${lg(p+'h',['#3A404C','#E4E9F0','#9AA4B4','#4E5868'],0,1)}</defs><g transform="translate(${x1} ${y1}) rotate(${f1(a)})">`;
   /* noBlade: just the hilt (the TV and phone app draws the glowing blade itself, in the colour of each profile's side) */
   if(!o.noBlade){s+=`<rect class="bc" x="${hl-2}" y="${f1(-bw*1.6)}" width="${f1(L-hl+2)}" height="${f1(bw*3.2)}" rx="${f1(bw*1.6)}" fill="${col}" opacity=".9" filter="url(#${p}g)"/>`;
   s+=`<rect class="bc" x="${hl-2}" y="${f1(-bw*.75)}" width="${f1(L-hl+2)}" height="${f1(bw*1.5)}" rx="${f1(bw*.75)}" fill="${col}"/><rect x="${hl-2}" y="${f1(-bw*.4)}" width="${f1(L-hl)}" height="${f1(bw*.8)}" rx="${f1(bw*.4)}" fill="#fff"/>`}
   s+=`<rect x="0" y="${f1(-bw*1.25)}" width="${hl}" height="${f1(bw*2.5)}" rx="2" fill="url(#${p}h)" stroke="#1A1E26" stroke-width="1"/>`;
   for(let k=0;k<5;k++)s+=`<rect x="${f1(4+k*hl*.11)}" y="${f1(-bw*1.25)}" width="${f1(hl*.05)}" height="${f1(bw*2.5)}" fill="#1A1E26"/>`;
   s+=`<rect x="${f1(hl*.66)}" y="${f1(-bw*1.55)}" width="${f1(hl*.14)}" height="${f1(bw*.5)}" rx="1" fill="#C0302A"/><rect x="${f1(hl-5)}" y="${f1(-bw*1.45)}" width="5" height="${f1(bw*2.9)}" rx="1" fill="#C9D2DE" stroke="#1A1E26" stroke-width=".6"/><rect x="-3" y="${f1(-bw*.9)}" width="4" height="${f1(bw*1.8)}" rx="1" fill="#4E5868"/>`;
   return s+`</g>`}
  function traveller(){/* a hooded traveller in long robes with a walking staff */
   let s='';const leg=(ph,c)=>`<g class="lg" data-x="0" data-p="-30" data-ph="${ph}"><rect x="-3" y="-30" width="6" height="27" rx="3" fill="${c}"/><path d="M-9 0V-4a3 3 0 0 1 3-3h6a3 3 0 0 1 3 3V0Z" fill="#2A1A10"/></g>`;
   s+=leg(1,'#4A3424');
   s+=`<g class="staff"><path d="M-22 0L-17 -96" stroke="#6A4A28" stroke-width="3.4" stroke-linecap="round"/><circle cx="-17" cy="-97" r="3.4" fill="#8A6A40"/></g>`;
   s+=`<path class="robe" d="M-12 -82C-16 -60 -20 -30 -18 -12L16 -12C18 -30 14 -60 10 -82Z" fill="#7A5434"/><path d="M-4 -80C-6 -55 -8 -30 -6 -12" stroke="#5A3A20" stroke-width="2" fill="none"/>`;
   s+=leg(0,'#5A4030');
   s+=`<path d="M-14 -78C-24 -66 -26 -50 -20 -40L-12 -46C-14 -56 -10 -66 -6 -74Z" fill="#6A4628"/><path d="M-20 -44L-18 -36" stroke="#C9A07A" stroke-width="4" stroke-linecap="round"/>`;
   s+=`<path d="M-15 -82C-17 -96 -9 -108 2 -108C12 -108 16 -98 14 -84C8 -78 -8 -77 -15 -82Z" fill="#8A6040"/><path d="M-12 -86C-12 -96 -6 -101 0 -101C-2 -94 -4 -88 -8 -84Z" fill="#1A0E08"/>`;
   return s}
  function speeder(){/* an open-top hover car gliding above the sand, with its shadow on the ground */
   let s=`<ellipse class="shd" cx="0" cy="0" rx="44" ry="4" fill="#000" opacity=".35"/><g class="hov">`;
   s+=`<path d="M-50 -24C-50 -32 -40 -36 -26 -36H30C44 -36 52 -30 52 -24C52 -18 44 -16 30 -16H-34C-44 -16 -50 -18 -50 -24Z" fill="#C86A2A" stroke="#5A2A10" stroke-width="1.2"/><path d="M-46 -26H48" stroke="#E8A060" stroke-width="2" opacity=".7"/>`;
   s+=`<path d="M-8 -36L4 -50L16 -50L18 -36Z" fill="#9FD8FF" opacity=".55" stroke="#C9D2DE"/><circle cx="24" cy="-44" r="6" fill="#E8C098"/><path d="M18 -46C18 -54 30 -54 30 -46Z" fill="#3A2A20"/>`;
   [[36,-30],[36,-20]].forEach(([x,y])=>{s+=`<rect x="${x}" y="${y-5}" width="20" height="9" rx="4" fill="#4E5868" stroke="#1A1E26"/><ellipse class="eg" cx="${x+21}" cy="${y-.5}" rx="5" ry="3" fill="#7FD0FF"/>`});
   return s+`</g>`}
  function lizard(){/* a big desert lizard carrying a rider: walks with diagonal leg pairs */
   let s='';const lgS=(x,ph,c)=>`<g class="lg" data-x="${x}" data-p="-26" data-ph="${ph}"><path d="M${x-5} -28L${x-4} -4L${x+4} -4L${x+5} -28Z" fill="${c}"/><path d="M${x-9} 0L${x-6} -6H${x+6}L${x+7} 0Z" fill="${c}"/></g>`;
   s+=lgS(-22,1,'#6A6A4A')+lgS(26,0,'#6A6A4A');
   s+=`<path d="M40 -40C60 -44 80 -34 96 -26C80 -30 62 -30 42 -30Z" fill="#8A8A62"/>`;
   s+=`<path d="M-40 -48C-36 -62 30 -64 44 -46C48 -36 40 -26 26 -24H-26C-38 -26 -44 -38 -40 -48Z" fill="#9A9A6E" stroke="#4A4A30" stroke-width="1.2"/>`;
   for(let k=0;k<6;k++)s+=`<path d="M${-30+k*12} -58l5 -7l5 7" fill="#7A7A52"/>`;
   s+=`<path d="M-40 -46C-52 -54 -62 -54 -70 -48C-74 -42 -70 -36 -60 -36C-52 -36 -44 -38 -38 -36Z" fill="#9A9A6E" stroke="#4A4A30" stroke-width="1.2"/><circle cx="-62" cy="-47" r="2.4" fill="#1A1410"/><path d="M-70 -40H-60" stroke="#4A4A30" stroke-width="1.2"/>`;
   s+=lgS(-30,0,'#8A8A62')+lgS(18,1,'#8A8A62');
   s+=`<path d="M-14 -62H18L14 -54H-10Z" fill="#5A3A20"/><path d="M-2 -62L-4 -48" stroke="#3A2414" stroke-width="3"/>`;
   s+=`<path d="M-6 -62C-8 -76 -2 -86 6 -86C12 -86 14 -76 12 -62Z" fill="#E8DCC0"/><path d="M-6 -64L-14 -54" stroke="#E8DCC0" stroke-width="5" stroke-linecap="round"/><circle cx="3" cy="-92" r="7" fill="#E8C098"/><path d="M-5 -94C-5 -102 11 -102 11 -94L13 -92H-7Z" fill="#C9B890"/><rect x="-3" y="-94" width="10" height="3" rx="1.5" fill="#1A1410"/>`;
   return s}
  function windowSVG(x,y,w,h,ws=1.2){const id=u('wn');const gy=h-40;const r=rnd(81);
   let s=`<defs><clipPath id="${id}c"><path d="M${x} ${y+h}V${y+w/2}A${w/2} ${w/2} 0 0 1 ${x+w} ${y+w/2}V${y+h}Z"/></clipPath>${lg(id+'s',['#2A1840','#7A3048','#E0703A','#FFC070'],0,1)}${blur(id+'g',12)}</defs>`;
   s+=`<g clip-path="url(#${id}c)"><g transform="translate(${x} ${y})"><rect width="${w}" height="${h}" fill="url(#${id}s)"/>`;
   for(let k=0;k<40;k++)s+=`<circle class="tw" data-k="${k}" cx="${f1(r()*w)}" cy="${f1(r()*h*.35)}" r="${f1(.6+r()*1.2)}" fill="#fff"/>`;
   s+=`<g class="ship" data-w="${w}" data-y="${f1(h*.18)}"><path d="M-46 0L-12 -3L-12 3Z" fill="#7FD0FF" opacity=".6"/>${fighter(0,0,30,90)}</g>`;
   const sy=gy-60;s+=`<circle cx="${f1(w*.38)}" cy="${sy}" r="60" fill="#FFE8B0" opacity=".45" filter="url(#${id}g)"/><circle cx="${f1(w*.38)}" cy="${sy}" r="30" fill="#FFF4D8"/><circle cx="${f1(w*.66)}" cy="${sy+22}" r="40" fill="#FFB060" opacity=".45" filter="url(#${id}g)"/><circle cx="${f1(w*.66)}" cy="${sy+22}" r="18" fill="#FFC27A"/>`;
   s+=`<path d="${smooth([[0,gy-38],[w*.2,gy-52],[w*.45,gy-30],[w*.7,gy-46],[w,gy-34]])}L${w} ${h}L0 ${h}Z" fill="#8A4A32"/>`;
   s+=`<path d="${smooth([[0,gy-12],[w*.35,gy-24],[w*.7,gy-8],[w,gy-18]])}L${w} ${h}L0 ${h}Z" fill="#6A3424"/><rect y="${gy}" width="${w}" height="${h-gy}" fill="#5A2C1E"/>`;
   s+=`<g class="walkers" data-w="${w}" data-gy="${gy}" data-s="${ws}"><g class="wk" data-off="0" data-k="tr">${traveller()}</g><g class="wk" data-off="9" data-k="sp">${speeder()}</g><g class="wk" data-off="18" data-k="lz">${lizard()}</g></g>`;
   s+=`</g></g>`;
   const F='#3A2414';s+=`<path d="M${x} ${y+h}V${y+w/2}A${w/2} ${w/2} 0 0 1 ${x+w} ${y+w/2}V${y+h}" fill="none" stroke="${F}" stroke-width="22"/><path d="M${x-14} ${y+h}V${y+w/2}A${w/2+14} ${w/2+14} 0 0 1 ${x+w+14} ${y+w/2}V${y+h}" fill="none" stroke="#5A3A24" stroke-width="5"/>`;
   s+=`<rect x="${x-30}" y="${y+h}" width="${w+60}" height="18" rx="4" fill="#6A4A30"/><rect x="${x-30}" y="${y+h}" width="${w+60}" height="4" fill="#8A6A48"/>`;
   return s}
  /* --- the corner: a hologram table and a laser sword resting on its stand --- */
  function holoTable(){const p=u('ht');let s=`<defs>${lg(p+'m',['#3A404C','#C9D2DE','#6E7888','#2A303A'],1,0)}${blur(p+'b',8)}<clipPath id="${p}k"><circle cx="0" cy="-250" r="62"/></clipPath><linearGradient id="${p}c" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#7FD8FF" stop-opacity=".45"/><stop offset="1" stop-color="#7FD8FF" stop-opacity="0"/></linearGradient></defs>`;
   s+=`<ellipse cx="0" cy="2" rx="90" ry="12" fill="#000" opacity=".5"/><path d="M-26 0L-14 -120H14L26 0Z" fill="url(#${p}m)" stroke="#1A1E26"/><ellipse cx="0" cy="0" rx="40" ry="8" fill="#3A404C"/>`;
   s+=`<ellipse cx="0" cy="-122" rx="92" ry="18" fill="#2A303A"/><ellipse cx="0" cy="-128" rx="92" ry="18" fill="url(#${p}m)" stroke="#1A1E26" stroke-width="1.4"/><ellipse cx="0" cy="-130" rx="34" ry="7" fill="#1A1E26"/><ellipse class="holoc" cx="0" cy="-131" rx="26" ry="5" fill="#7FD8FF"/>`;
   s+=`<path d="M-26 -131L-70 -250L70 -250L26 -131Z" fill="url(#${p}c)"/>`;
   s+=`<g class="holo"><circle cx="0" cy="-250" r="72" fill="#4FB8FF" opacity=".22" filter="url(#${p}b)"/><circle cx="0" cy="-250" r="62" fill="#4FB8FF" opacity=".22" stroke="#BFEFFF" stroke-width="2"/>`;
   s+=`<g clip-path="url(#${p}k)" stroke="#BFEFFF" fill="none">`;for(let k=0;k<6;k++)s+=`<ellipse class="mer" data-k="${k}" cx="0" cy="-250" rx="62" ry="62" stroke-width="1.4" opacity=".75"/>`;for(let k=-2;k<=2;k++)s+=`<path d="M-64 ${-250+k*20}H64" stroke-width="1.2" opacity=".6"/>`;
   s+=`<circle cx="18" cy="-268" r="16" fill="#9FE4FF" opacity=".35" stroke="none"/>`;for(let yy=-312;yy<-188;yy+=4)s+=`<path d="M-64 ${yy}H64" stroke-width=".6" opacity=".25"/>`;s+=`</g>`;
   s+=`<circle class="moon" cx="0" cy="-250" r="7" fill="#BFEFFF" opacity=".8"/></g>`;
   return s}
  function saberStand(col,noBlade){const p=u('ss');let s=`<defs>${lg(p+'w',['#4A2E18','#8A5A32','#5A3A20'],0,1)}</defs>`;
   s+=`<ellipse cx="0" cy="3" rx="80" ry="9" fill="#000" opacity=".5"/><rect x="-62" y="-70" width="124" height="70" rx="4" fill="url(#${p}w)" stroke="#2A1A0C" stroke-width="1.4"/><path d="M-62 -48H62M-62 -24H62" stroke="#2A1A0C" stroke-width="1.4" opacity=".6"/>`;
   s+=`<path d="M-40 -70V-86M30 -70V-86" stroke="#4E5868" stroke-width="5"/><path d="M-48 -90A8 8 0 0 0 -32 -90M22 -90A8 8 0 0 0 38 -90" stroke="#4E5868" stroke-width="4" fill="none"/>`;
   s+=`<g class="blade">${saber(-60,-92,180,-92,col,{hilt:96,w:7,noBlade})}</g>`;
   return s}
  function corner(sc,col,noBlade){return `<g transform="scale(${sc})"><ellipse cx="-60" cy="30" rx="380" ry="50" fill="#5A2A1E" opacity=".55"/><ellipse cx="-60" cy="30" rx="340" ry="40" fill="none" stroke="#E0A060" stroke-opacity=".25" stroke-width="4"/>
   <g transform="translate(-250 0)">${saberStand(col,noBlade)}</g><g transform="translate(60 0)">${holoTable()}</g></g>`}
  function room(W,H,win){const id=u('rm');const fl=Math.round(H*.8);
   let s=`<defs>${lg(id+'w',['#120C0A','#22160F','#2E1E14'],0,1)}${lg(id+'f',['#3A2A1E','#1A120C'],0,1)}<radialGradient id="${id}v" cx=".5" cy=".45" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".62"/></radialGradient><radialGradient id="${id}l" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#FFB060" stop-opacity=".22"/><stop offset="1" stop-color="#FFB060" stop-opacity="0"/></radialGradient></defs>`;
   s+=`<rect width="${W}" height="${H}" fill="url(#${id}w)"/>`;
   /* the curved dome ceiling and smooth plaster */
   s+=`<path d="M0 ${f1(H*.16)}Q${W/2} ${f1(-H*.08)} ${W} ${f1(H*.16)}" stroke="#3A2618" stroke-width="${f1(H*.012)}" fill="none" opacity=".8"/><path d="M0 ${f1(H*.24)}Q${W/2} ${f1(H*.02)} ${W} ${f1(H*.24)}" stroke="#000" stroke-width="2" fill="none" opacity=".25"/>`;
   if(W>600){/* two plaster wall niches with pots, left side */[[150,300],[360,250]].forEach(([nx,ny])=>{s+=`<path d="M${nx-44} ${ny+70}V${ny}A44 44 0 0 1 ${nx+44} ${ny}V${ny+70}Z" fill="#0E0806"/><path d="M${nx-44} ${ny+70}H${nx+44}" stroke="#4A3220" stroke-width="5"/><path d="M${nx-14} ${ny+68}C${nx-22} ${ny+50} ${nx-12} ${ny+34} ${nx} ${ny+34}C${nx+12} ${ny+34} ${nx+22} ${ny+50} ${nx+14} ${ny+68}Z" fill="#7A4A2E"/><rect x="${nx-7}" y="${ny+28}" width="14" height="8" fill="#6A3E24"/>`})}
   if(win)s+=windowSVG(...win);
   s+=`<rect y="${fl}" width="${W}" height="${H-fl}" fill="url(#${id}f)"/>`;for(let k=1;k<5;k++)s+=`<path d="M0 ${f1(fl+(H-fl)*k/5)} H${W}" stroke="#000" stroke-opacity=".28" stroke-width="2"/>`;for(let x=0;x<W;x+=W/12)s+=`<path d="M${f1(x)} ${fl}L${f1(x-(W/2-x)*.4)} ${H}" stroke="#000" stroke-opacity=".18" stroke-width="2"/>`;
   s+=`<rect y="${fl-10}" width="${W}" height="10" fill="#1A100A"/>`;
   return {bg:s,vig:`<rect width="${W}" height="${H}" fill="url(#${id}v)"/>`}}

  /**
   * The two shapes: wide (TVs, computers) and tall (phones). blade: where the laser sword's blade is
   * (x from, x to, y, thickness), which the TV and phone app uses to draw the blade over the video
   * (ui/setup/StarWarsRoom.kt has the same numbers).
   */
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1600, 170, 270, 420, 1.25], corner: [400, 878, 1], blade: [184, 330, 786, 7] },
    tall: { w: 390, h: 844, win: null, corner: [220, 812, 0.45], blade: [122.8, 188.5, 770.6, 3.15] },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t), blade(side) }: at poses everything at t seconds,
   * blade("light"|"dark") recolours the laser sword.
   * opts: { layout: "wide"|"tall", side: "light"|"dark", noBlade: true (hilt only, for the app's videos) }
   */
  function build(svg, opts) {
    const o = opts || {};
    const L = LAYOUTS[o.layout || "wide"];
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", o.align || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win);
    svg.innerHTML = rm.bg + `<g transform="translate(${L.corner[0]} ${L.corner[1]})">${corner(L.corner[2], BLADES[o.side] || BLADES.light, !!o.noBlade)}</g>` + rm.vig;
    const scene = pose(svg);
    scene.blade = (side) => svg.querySelectorAll(".bc").forEach((r) => r.setAttribute("fill", BLADES[side] || BLADES.light));
    return scene;
  }

  /* a steady made-up "random" number for whole number n (so the flicker repeats with the loop) */
  const hash = (n) => { const x = Math.sin(n * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };

  /* gathers every moving part, and returns at(t) */
  function pose(root) {
    const q = (s) => [...root.querySelectorAll(s)];
    const tw = q(".tw"), ships = q(".ship"), mers = q(".mer"), moons = q(".moon"), holos = q(".holo"), hcs = q(".holoc"), blades = q(".blade");
    const walkers = q(".walkers").map((W) => ({ ww: +W.dataset.w, gy: +W.dataset.gy, s: +W.dataset.s, wk: [...W.querySelectorAll(".wk")].map((g) => ({ g, off: +g.dataset.off, k: g.dataset.k, lg: [...g.querySelectorAll(".lg")], hov: g.querySelector(".hov"), shd: g.querySelector(".shd"), eg: [...g.querySelectorAll(".eg")], staff: g.querySelector(".staff"), robe: g.querySelector(".robe") })) }));
    const twS = [0, 1, 2, 3, 4].map((k) => wq(0.6 + k * 0.2));
    const s14 = wq(1.4), s09 = wq(0.9), s9 = wq(9), s6 = wq(6), s23 = wq(23), s7 = wq(7);
    function at(t) {
      t = ((t % LOOP) + LOOP) % LOOP;
      tw.forEach((c) => { const k = +c.dataset.k; c.setAttribute("opacity", (0.3 + 0.5 * Math.abs(Math.sin(t * twS[k % 5] + k))).toFixed(2)); });
      ships.forEach((S) => { const w = +S.dataset.w, y = +S.dataset.y; const P = LOOP / 2, a = (t % P) / P; S.setAttribute("transform", `translate(${f1(-80 + a * (w + 160))} ${f1(y + Math.sin(t * s14) * 4)})`); S.setAttribute("opacity", a < 0.65 ? "1" : "0"); });
      mers.forEach((m) => { const k = +m.dataset.k; const ph = ((t / 3 + k / 6) % 1); m.setAttribute("rx", f1(Math.abs(Math.cos(ph * Math.PI)) * 62)); });
      moons.forEach((m) => { const a = t * s09; m.setAttribute("cx", f1(Math.cos(a) * 84)); m.setAttribute("cy", f1(-250 + Math.sin(a) * 22)); m.setAttribute("opacity", Math.sin(a) > 0 ? ".9" : ".35"); });
      const flick = hash(Math.floor(t * 12)) < 0.04 ? 0.55 : 1;
      holos.forEach((h) => h.setAttribute("opacity", (flick * (0.85 + 0.15 * Math.sin(t * s9))).toFixed(2)));
      hcs.forEach((h) => h.setAttribute("opacity", (0.7 + 0.3 * Math.sin(t * s6)).toFixed(2)));
      blades.forEach((b) => b.setAttribute("opacity", (0.92 + 0.08 * Math.sin(t * s23) * Math.sin(t * s7)).toFixed(3)));
      walkers.forEach((W) => {
        const Pw = LOOP, v = (W.ww + 300) / 8;
        W.wk.forEach((K) => {
          const tt = ((t + Pw - K.off) % Pw); const x = W.ww + 150 - tt * v; const step = K.k === "lz" ? 4.2 : 6;
          const bob = K.k === "sp" ? 0 : Math.abs(Math.sin(tt * step)) * 1.4;
          K.g.setAttribute("transform", `translate(${f1(x)} ${f1(W.gy + 2 - bob)}) scale(${W.s})`);
          K.lg.forEach((l) => { const sw = Math.sin(tt * step + (+l.dataset.ph) * Math.PI) * (K.k === "lz" ? 18 : 22); l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} ${l.dataset.p})`); });
          if (K.hov) { const hb = Math.sin(tt * 2.6) * 3; K.hov.setAttribute("transform", `translate(0 ${f1(hb)})`); K.shd.setAttribute("rx", f1(44 - hb)); K.eg.forEach((e, i) => e.setAttribute("opacity", (0.6 + 0.4 * hash(Math.floor(tt * 20) + i * 7)).toFixed(2))); }
          if (K.staff) K.staff.setAttribute("transform", `rotate(${f1(Math.sin(tt * step) * 8)} -22 0)`);
          if (K.robe) K.robe.setAttribute("transform", `skewX(${f1(Math.sin(tt * step * 2) * 2)})`);
        });
      });
    }
    return { at };
  }

  /** Plays a built room live until the element leaves the page */
  function play(svg, scene) {
    const start = performance.now() - 4000;
    const tick = (now) => { if (!svg.isConnected) return; scene.at((now - start) / 1000); requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
  }

  G.BenflixStarWars = { LOOP, season, greeting, build, play, LAYOUTS, BLADES };
})(typeof window !== "undefined" ? window : globalThis);
