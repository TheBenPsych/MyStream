/* Benflix National Popcorn Day: the Who's watching room (approved 2026-11-17, thread "Design a Popcorn Day theme for Benflix").
 *
 * A dim, warm room with cinema-red walls and wood panelling. Bottom-left stands an old-style popcorn cart: its
 * steel kettle hangs from the roof of the glass case, the little lids flap as pieces pop out, arc through the air
 * and land on the heap, and every 12 seconds the kettle tips over and pours a whole batch out. The sign bulbs
 * flash, a striped bucket stands on the floor beside it. Out of the window it's a snowy January evening: a
 * little cinema across the road has chasing bulbs, and people walk past (a kid hugging a huge bucket, two people
 * sharing a bag, someone walking a dog, and a popcorn seller pushing a cart).
 * The heap grows through the day: a little in the morning, half full after lunch, overflowing in the evening
 * with a filled bowl on the floor. Just 19 January, Kids too.
 *
 * Like feast.js, the same drawing is used live on the web and by tools/popcorn/record.js, which records it as
 * looping videos for the TV and phone app (one for each part of the day). Everything is a function of the time t
 * (seconds) and repeats every LOOP seconds, so a video of one loop joins up with no jump.
 * The drawing code comes straight from the approved design sheets (themes/popcorn/src/kit.js and sheet2.js).
 */
(function (G) {
  "use strict";
  const LOOP = 36;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;
  /** a repeat time that fits a whole number of times into one loop, close to p seconds */
  const pq = (p) => LOOP / Math.max(1, Math.round(LOOP / p));

  // ---------- dates ----------
  /** Just 19 January, midnight to midnight */
  const season = (d) => d.getMonth() === 0 && d.getDate() === 19;
  const greeting = () => "Happy Popcorn Day";
  const line = () => "It’s National Popcorn Day · grab a bowl";
  /** how full the cart is: "am" before noon, "pm" until 5 PM, then "eve" */
  const variant = (d) => (d.getHours() < 12 ? "am" : d.getHours() < 17 ? "pm" : "eve");

  // ---------- drawing kit (from the approved design sheet) ----------
  /* ===== shared helpers for the Popcorn Day sheets ===== */
  let UID=0;const u=p=>p+(UID++)+'_';
  const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
  const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
  const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
  const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
  const f1=v=>(+v).toFixed(1);
  const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
  const sh=(id,s)=>`<filter id="${id}" x="-40%" y="-40%" width="180%" height="180%"><feDropShadow dx="0" dy="${f1(s*.6)}" stdDeviation="${s}" flood-color="#000" flood-opacity=".5"/></filter>`;
  function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
  const T=(x,y,s,txt,o={})=>`<text x="${x}" y="${y}" font-family="${o.font||'Inter,sans-serif'}" font-weight="${o.w||600}" font-size="${s}" fill="${o.fill||'#F5F0EA'}" ${o.anchor?`text-anchor="${o.anchor}"`:''} ${o.sh?'filter="url(#Gts)"':''} ${o.op?`opacity="${o.op}"`:''}>${txt}</text>`;

  /* ---------- popcorn ----------
     Flavour colours: [light, middle, edge]. Shared gradients live in GDEF so hundreds of pieces stay fast. */
  const FL={plain:['#FFFDF6','#F4E6C2','#B89A5E'],butter:['#FFF7CF','#F6D978','#B98C26'],caramel:['#FBD28C','#DC9338','#7E4512'],cheddar:['#FFD796','#F39B30','#A9531A'],kettle:['#FFFFFF','#F1EADB','#B3A586'],
   pink:['#FFE3EC','#F7A9C2','#B5597A'],blue:['#E3F1FF','#9CCBF5','#4E7FB0'],mint:['#E6FFF1','#9EE3BD','#3F9A6C'],choc:['#FFFDF6','#F4E6C2','#B89A5E']};
  const FLK=Object.keys(FL);
  const GDEF=`<defs><filter id="Gts" x="-10%" y="-40%" width="120%" height="180%"><feDropShadow dx="0" dy="2" stdDeviation="2.5" flood-color="#000" flood-opacity=".55"/></filter>
  ${FLK.map(k=>`<radialGradient id="pg_${k}" cx=".36" cy=".3" r=".8"><stop offset="0" stop-color="${FL[k][0]}"/><stop offset=".62" stop-color="${FL[k][1]}"/><stop offset="1" stop-color="${mix(FL[k][1],FL[k][2],.55)}"/></radialGradient>`).join('')}
  <radialGradient id="pg_hull" cx=".4" cy=".35" r=".8"><stop offset="0" stop-color="#E8A24A"/><stop offset="1" stop-color="#8A4A14"/></radialGradient>
  <linearGradient id="pg_side" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#000" stop-opacity=".38"/><stop offset=".22" stop-color="#000" stop-opacity="0"/><stop offset=".62" stop-color="#fff" stop-opacity=".16"/><stop offset=".78" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".45"/></linearGradient>
  <linearGradient id="pg_tin" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#6A5214"/><stop offset=".3" stop-color="#F6DA86"/><stop offset=".55" stop-color="#C99A34"/><stop offset="1" stop-color="#5A400C"/></linearGradient>
  <linearGradient id="pg_steel" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#4A5260"/><stop offset=".3" stop-color="#F2F5FA"/><stop offset=".55" stop-color="#AEB7C4"/><stop offset=".8" stop-color="#E4E9F0"/><stop offset="1" stop-color="#4A5260"/></linearGradient>
  </defs>`;
  /* One popped kernel: 4 to 6 puffy lobes around a middle, a crease between lobes, sometimes a bit of brown hull.
     x,y = middle, s = size across. Always opaque. */
  function puff(x,y,s,fl='plain',seed=1,o={}){const r=rnd(seed*7919+3);const n=4+Math.floor(r()*3);const k=fl;const g=`url(#pg_${k})`;const edge=FL[k][2];
   let lob=[];const rot=r()*Math.PI*2;
   for(let i=0;i<n;i++){const a=rot+i/n*Math.PI*2+(r()-.5)*.5,d=s*(.17+r()*.1),rr=s*(.2+r()*.1);lob.push([x+Math.cos(a)*d,y+Math.sin(a)*d*.9,rr,a])}
   lob.sort((p,q)=>p[1]-q[1]);
   let out=`<circle cx="${f1(x)}" cy="${f1(y)}" r="${f1(s*.3)}" fill="${g}"/>`;
   lob.forEach(([lx,ly,rr])=>{out+=`<circle cx="${f1(lx)}" cy="${f1(ly)}" r="${f1(rr)}" fill="${g}" stroke="${edge}" stroke-width="${f1(Math.max(.4,s*.018))}" stroke-opacity=".55"/>`});
   /* top lobe over the middle so it reads as a puffy cluster, not a flower */
   out+=`<circle cx="${f1(x-s*.04)}" cy="${f1(y-s*.06)}" r="${f1(s*.2)}" fill="${g}"/>`;
   out+=`<path d="M${f1(x-s*.12)} ${f1(y+s*.02)} q${f1(s*.1)} ${f1(s*.08)} ${f1(s*.2)} ${f1(-s*.02)}" stroke="${edge}" stroke-opacity=".5" stroke-width="${f1(Math.max(.4,s*.03))}" fill="none" stroke-linecap="round"/>`;
   if(!o.nohull&&(k==='plain'||k==='butter'||k==='kettle'||k==='choc')&&r()<.75){const a=rot+Math.PI*.8;const hx_=x+Math.cos(a)*s*.33,hy=y+Math.sin(a)*s*.3;
    out+=`<path d="M${f1(hx_-s*.09)} ${f1(hy)} Q${f1(hx_)} ${f1(hy-s*.14)} ${f1(hx_+s*.09)} ${f1(hy)} Q${f1(hx_)} ${f1(hy+s*.06)} ${f1(hx_-s*.09)} ${f1(hy)}Z" fill="url(#pg_hull)" transform="rotate(${f1(a*57.3+90)} ${f1(hx_)} ${f1(hy)})"/>`}
   if(k==='kettle'){for(let i=0;i<4;i++)out+=`<circle cx="${f1(x+(r()-.5)*s*.5)}" cy="${f1(y+(r()-.5)*s*.4)}" r="${f1(s*.035)}" fill="#fff"/>`}
   if(k==='caramel'||k==='candy')out+=`<ellipse cx="${f1(x-s*.1)}" cy="${f1(y-s*.16)}" rx="${f1(s*.08)}" ry="${f1(s*.05)}" fill="#fff" opacity=".75"/>`;
   out+=`<circle cx="${f1(x-s*.12)}" cy="${f1(y-s*.16)}" r="${f1(s*.07)}" fill="#fff" opacity=".6"/>`;
   return out}
  /* An unpopped kernel: a shiny golden-orange teardrop with a pale tip */
  function kernel(x,y,s,rot=0){return `<g transform="rotate(${rot} ${f1(x)} ${f1(y)})"><path d="M${f1(x)} ${f1(y-s*.55)} C${f1(x+s*.5)} ${f1(y-s*.4)} ${f1(x+s*.45)} ${f1(y+s*.4)} ${f1(x)} ${f1(y+s*.5)} C${f1(x-s*.45)} ${f1(y+s*.4)} ${f1(x-s*.5)} ${f1(y-s*.4)} ${f1(x)} ${f1(y-s*.55)}Z" fill="url(#pg_hull)" stroke="#6A3810" stroke-width="${f1(s*.04)}"/>
   <path d="M${f1(x-s*.12)} ${f1(y+s*.28)} Q${f1(x)} ${f1(y+s*.5)} ${f1(x+s*.12)} ${f1(y+s*.28)}" fill="#F6E2B0"/><ellipse cx="${f1(x-s*.12)}" cy="${f1(y-s*.2)}" rx="${f1(s*.08)}" ry="${f1(s*.16)}" fill="#fff" opacity=".6"/></g>`}
  /* A heap of popcorn resting on a surface: by = surface, w = width, h = height of the mound. Back pieces drawn first. */
  function pile(cx,by,w,h,n,fl='plain',seed=1,s=null){const r=rnd(seed);const P=[];const sz=s||Math.max(8,w/5.2);
   /* fill the mound row by row so it is solid, then a puffy crown on top */
   const rows=Math.max(1,Math.round((h-sz*.3)/(sz*.55)));
   for(let j=0;j<rows;j++){const yy=by-sz*.3-j*sz*.55;const lev=(by-yy)/h;const half=w/2*.92*Math.sqrt(Math.max(0,1-lev/1.02));const cnt=Math.max(1,Math.round(half*2/(sz*.62)));
    for(let i=0;i<cnt;i++){const x=cx-half+(i+.5)*(2*half/cnt)+(r()-.5)*sz*.25;P.push([x,yy+(r()-.5)*sz*.2,sz*(.85+r()*.3)])}}
   const cr=Math.max(3,Math.round(w/(sz*.75)));
   for(let i=0;i<cr;i++){const t=(i/(cr-1))*1.8-.9;P.push([cx+t*w/2,by-h*(1-t*t*.9)+sz*.25+(r()-.5)*sz*.2,sz*(.9+r()*.25)])}
   P.sort((a,b)=>a[1]-b[1]);
   const F=fl==='candy'?['pink','blue','mint','butter']:fl==='choc'?['plain']:[fl];
   let out=P.map((p,i)=>puff(p[0],p[1],p[2],F[i%F.length],seed*31+i)).join('');
   if(fl==='choc'){/* melted chocolate drizzled across the whole heap, following its curve */
    for(let j=0;j<3;j++){const lev=.3+j*.24;let d='';const N=40;for(let i=0;i<=N;i++){const t=i/N*1.5-.75;const y=by-h*lev*(1-t*t*.9)-h*.08+Math.sin(t*9+j*2)*sz*.22;d+=(i?'L':'M')+f1(cx+t*w/2*(1-lev*.3))+' '+f1(y)}
     out+=`<path d="${d}" stroke="#4A2412" stroke-width="${f1(sz*.16)}" fill="none" stroke-linecap="round" stroke-linejoin="round"/><path d="${d}" stroke="#8A5232" stroke-width="${f1(sz*.05)}" fill="none" transform="translate(0 ${f1(-sz*.04)})" stroke-linecap="round"/>`}}
   return out}
  const groundShadow=(x,y,w)=>`<ellipse cx="${f1(x)}" cy="${f1(y)}" rx="${f1(w)}" ry="${f1(w*.12)}" fill="#000" opacity=".32"/>`;

  /* ---------- the six snack items, all drawn standing on y=0, centred on x=0, about 100 tall ---------- */
  const RED='#D3262E',RED2='#8E141B',CREAM='#FBF5EA';
  function bucket(fl='butter',seed=4){const p=u('bk');const tw=40,bw=29,h=68;
   let s=`<defs><clipPath id="${p}c"><path d="M${-bw} 0 L${-tw} ${-h} L${tw} ${-h} L${bw} 0Z"/></clipPath></defs>`+groundShadow(0,1,40);
   s+=`<g clip-path="url(#${p}c)"><rect x="-44" y="${-h}" width="88" height="${h}" fill="${CREAM}"/>`;
   for(let i=-4;i<=4;i+=2){s+=`<path d="M${f1(i*bw/5)} 0 L${f1(i*tw/5)} ${-h} L${f1((i+1)*tw/5)} ${-h} L${f1((i+1)*bw/5)} 0Z" fill="${RED}"/>`}
   s+=`<rect x="-44" y="${-h}" width="88" height="${h}" fill="url(#pg_side)"/></g>`;
   s+=`<path d="M${-bw} 0 Q0 5 ${bw} 0" stroke="${RED2}" stroke-width="2" fill="none"/>`;
   /* rolled rim, then the popcorn heaped above it and spilling a little over the front */
   s+=pile(0,-h+3,84,30,26,fl,seed);
   s+=`<path d="M${-tw-1} ${-h} Q0 ${-h+6} ${tw+1} ${-h}" stroke="${CREAM}" stroke-width="5" fill="none" stroke-linecap="round"/><path d="M${-tw} ${-h+3} Q0 ${-h+9} ${tw} ${-h+3}" stroke="#000" stroke-opacity=".18" stroke-width="2" fill="none"/>`;
   s+=puff(-30,-h+8,13,fl,seed+50)+puff(26,-h+10,12,fl,seed+51);
   return s}
  function microBag(fl='butter',seed=5){const p=u('mw');const w=34,h=74;
   let s=groundShadow(0,1,38);
   /* puffed paper bag: bulging sides, square bottom, torn open along the top */
   const body=`M${-w+4} 0 C${-w-4} ${-h*.35} ${-w-4} ${-h*.7} ${-w+2} ${-h} L${w-2} ${-h} C${w+4} ${-h*.7} ${w+4} ${-h*.35} ${w-4} 0Z`;
   s+=`<defs><clipPath id="${p}c"><path d="${body}"/></clipPath><linearGradient id="${p}g" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#CFC6B6"/><stop offset=".35" stop-color="#FFFDF8"/><stop offset=".7" stop-color="#F2ECE2"/><stop offset="1" stop-color="#B9B0A0"/></linearGradient></defs>`;
   s+=pile(0,-h+8,62,26,20,fl,seed);
   s+=`<path d="${body}" fill="url(#${p}g)"/><g clip-path="url(#${p}c)"><rect x="-40" y="${-h*.62}" width="80" height="20" fill="${RED}"/><rect x="-40" y="${-h*.62+20}" width="80" height="4" fill="#F6C744"/>
   <path d="M-40 ${-h*.18} H40" stroke="#000" stroke-opacity=".12" stroke-width="2"/><path d="M-6 0 L-2 ${-h}" stroke="#000" stroke-opacity=".08" stroke-width="3"/></g>`;
   /* torn top edge */
   let tz=`M${-w+2} ${-h}`;for(let i=1;i<=8;i++){tz+=` L${f1(-w+2+i*(2*w-4)/8)} ${f1(-h+(i%2?-5:2))}`}
   s+=`<path d="${tz}" stroke="#E6DED0" stroke-width="3" fill="none" stroke-linejoin="round"/>`;
   s+=puff(-14,-h-6,14,fl,seed+60)+puff(10,-h-10,15,fl,seed+61)+puff(-1,-h-16,13,fl,seed+62);
   return s}
  function machine(fl='butter',seed=6,o={}){/* a little old-style popcorn cart: red cabinet on wheels, glass case, steel kettle hanging inside, red roof */
   const p=u('mc');const W=36,cab=30,glassH=56,roofH=14,top=-(cab+glassH);
   let s=groundShadow(0,1,44);
   s+=`<defs><linearGradient id="${p}r" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#7A1016"/><stop offset=".35" stop-color="#E23A3A"/><stop offset=".6" stop-color="#C21F27"/><stop offset="1" stop-color="#6A0E14"/></linearGradient><radialGradient id="${p}l" cx=".5" cy=".2" r=".9"><stop offset="0" stop-color="#FFE7A8" stop-opacity=".55"/><stop offset="1" stop-color="#FFE7A8" stop-opacity=".06"/></radialGradient></defs>`;
   /* wheels behind the cabinet */
   s+=`<circle cx="${-W+8}" cy="-6" r="7" fill="#1A1416" stroke="#C9A64A" stroke-width="2"/><circle cx="${W-8}" cy="-6" r="7" fill="#1A1416" stroke="#C9A64A" stroke-width="2"/>`;
   s+=`<rect x="${-W}" y="${-cab}" width="${2*W}" height="${cab-6}" rx="3" fill="url(#${p}r)"/><rect x="${-W+6}" y="${-cab+6}" width="${2*W-12}" height="${cab-18}" rx="2" fill="none" stroke="#F6C744" stroke-width="1.6"/>`;
   /* glass case: popcorn lies on its floor, kettle hangs from the roof on a rod */
   s+=`<rect x="${-W}" y="${top}" width="${2*W}" height="${glassH}" fill="#2A1A14"/><rect x="${-W}" y="${top}" width="${2*W}" height="${glassH}" fill="url(#${p}l)"/>`;
   s+=pile(0,-cab-1,2*W-6,o.full?34:20,o.full?34:22,fl,seed);
   const ky=top+16;s+=`<rect x="-1.4" y="${top}" width="2.8" height="8" fill="#9AA4B4"/><path d="M-15 ${ky-6} H15 L13 ${ky+10} Q0 ${ky+15} -13 ${ky+10}Z" fill="url(#pg_steel)"/><path d="M-16 ${ky-6} Q0 ${ky-13} 16 ${ky-6}Z" fill="#C6CED9" stroke="#6A7484" stroke-width=".8"/>`;
   if(!o.still)s+=puff(-8,ky-16,10,fl,seed+70)+puff(9,ky-20,9,fl,seed+71);
   /* glass shine + gold corner posts */
   s+=`<path d="M${-W+6} ${top+4} L${-W+18} ${top+4} L${-W+6} ${-cab-6}Z" fill="#fff" opacity=".12"/>`;
   s+=[-W,W].map(x=>`<rect x="${x-2.5}" y="${top}" width="5" height="${glassH}" fill="#D9B14A" stroke="#6A4A10" stroke-width=".6"/>`).join('');
   /* roof with a little crown */
   s+=`<path d="M${-W-5} ${top} L${-W+4} ${top-roofH} H${W-4} L${W+5} ${top}Z" fill="url(#${p}r)"/><rect x="${-W-5}" y="${top-2}" width="${2*W+10}" height="4" fill="#F6C744"/><circle cx="0" cy="${top-roofH-5}" r="5" fill="#F6C744" stroke="#6A4A10" stroke-width=".8"/>`;
   return s}
  function bowl(fl='butter',seed=7){const p=u('bw');const R=46,h=30;
   let s=groundShadow(0,1,36);
   s+=`<defs><linearGradient id="${p}g" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#6A0E14"/><stop offset=".3" stop-color="#E5403A"/><stop offset=".55" stop-color="#C8222A"/><stop offset="1" stop-color="#5A0A10"/></linearGradient></defs>`;
   s+=`<path d="M-22 0 H22 L24 -4 H-24Z" fill="#7A1016"/>`;
   s+=pile(0,-h+2,88,40,30,fl,seed);
   s+=`<path d="M${-R} ${-h} Q${-R+4} -2 -20 -3 H20 Q${R-4} -2 ${R} ${-h} Q0 ${-h+8} ${-R} ${-h}Z" fill="url(#${p}g)"/>`;
   s+=`<path d="M${-R+4} ${-h+10} Q0 ${-h+18} ${R-4} ${-h+10}" stroke="${CREAM}" stroke-width="4" fill="none"/>`;
   s+=`<path d="M${-R} ${-h} Q0 ${-h+8} ${R} ${-h}" stroke="#F4A0A0" stroke-width="2" fill="none" opacity=".8"/>`;
   s+=puff(-R+6,-h-2,13,fl,seed+80)+puff(R-8,-h,12,fl,seed+81);
   return s}
  function tin(fl='caramel',seed=8){/* round popcorn tin, gold rim, lid leaning against its side on the floor */
   const p=u('tn');const w=30,h=58;
   let s=groundShadow(4,1,46);
   s+=`<defs><linearGradient id="${p}g" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#14203E"/><stop offset=".35" stop-color="#3A5AA8"/><stop offset=".6" stop-color="#26407E"/><stop offset="1" stop-color="#0E1830"/></linearGradient></defs>`;
   s+=pile(0,-h+4,60,26,20,fl,seed);
   s+=`<path d="M${-w} ${-h} V-4 Q0 4 ${w} -4 V${-h} Q0 ${-h+7} ${-w} ${-h}Z" fill="url(#${p}g)"/>`;
   /* painted stars band */
   for(let i=-2;i<=2;i++){const x=i*11,y=-h*.5;let d='';for(let k=0;k<10;k++){const a=-Math.PI/2+k*Math.PI/5,rr=k%2?2.4:5.4;d+=(k?'L':'M')+f1(x+Math.cos(a)*rr)+' '+f1(y+Math.sin(a)*rr)}s+=`<path d="${d}Z" fill="#F6C744" opacity="${1-Math.abs(i)*.2}"/>`}
   s+=`<path d="M${-w} ${-h+4} Q0 ${-h+11} ${w} ${-h+4}" stroke="url(#pg_tin)" stroke-width="4" fill="none"/><path d="M${-w} -8 Q0 0 ${w} -8" stroke="url(#pg_tin)" stroke-width="4" fill="none"/>`;
   /* lid leaning on the right side */
   s+=`<g transform="rotate(-14 ${w+10} 0)"><ellipse cx="${w+8}" cy="-24" rx="6" ry="25" fill="#26407E" stroke="url(#pg_tin)" stroke-width="3"/><ellipse cx="${w+6}" cy="-24" rx="2" ry="18" fill="#fff" opacity=".15"/></g>`;
   return s}
  function theatreBox(fl='butter',seed=9){/* tall cardboard cinema box, open flaps standing up */
   const p=u('tb');const w=26,h=66;
   let s=groundShadow(0,1,34);
   s+=`<path d="M${-w} ${-h} L${-w-8} ${-h-16} L${-w+4} ${-h-14} L${-w+2} ${-h}Z" fill="#E7B83A"/><path d="M${w} ${-h} L${w+8} ${-h-16} L${w-4} ${-h-14} L${w-2} ${-h}Z" fill="#C99A28"/>`;
   s+=pile(0,-h+6,58,24,18,fl,seed);
   s+=`<rect x="${-w}" y="${-h}" width="${2*w}" height="${h}" fill="#F6C744"/><rect x="${-w}" y="${-h}" width="${2*w}" height="${h}" fill="url(#pg_side)"/>`;
   s+=`<rect x="${-w+6}" y="${-h+12}" width="${2*w-12}" height="${h-20}" rx="3" fill="${RED}"/>`;
   /* little film strip printed on the front */
   s+=`<rect x="${-w+10}" y="${-h*.55}" width="${2*w-20}" height="16" fill="#1A1416"/>${[0,1,2].map(i=>`<rect x="${-w+13+i*11}" y="${-h*.55+4}" width="8" height="8" fill="#F6C744" opacity=".85"/>`).join('')}`;
   s+=`<path d="M${-w} ${-h} H${w}" stroke="#B8891E" stroke-width="2"/>`;
   s+=puff(-4,-h-6,13,fl,seed+90)+puff(12,-h-2,12,fl,seed+91);
   return s}

  /* notice marks from the look sheet: a ticket for new episodes, a film reel for Live TV */
  function ticket(x,y,s,c){return `<g transform="translate(${x} ${y}) rotate(-12) scale(${s/30})"><path d="M-15 -10 H15 V-3 a3 3 0 0 0 0 6 V10 H-15 V3 a3 3 0 0 0 0 -6Z" fill="${c}"/><path d="M6 -10 V10" stroke="#fff" stroke-width="1.4" stroke-dasharray="2 2"/><path d="M-10 -3 H2 M-10 2 H-1" stroke="#fff" stroke-width="1.8" opacity=".85"/></g>`}
  function reel(x,y,r,c){let s=`<circle cx="${x}" cy="${y}" r="${r}" fill="${c}"/>`;for(let k=0;k<5;k++){const a=k/5*Math.PI*2-Math.PI/2;s+=`<circle cx="${f1(x+Math.cos(a)*r*.55)}" cy="${f1(y+Math.sin(a)*r*.55)}" r="${f1(r*.2)}" fill="#1E0C0E"/>`}return s+`<circle cx="${x}" cy="${y}" r="${f1(r*.14)}" fill="#1E0C0E"/>`}

  // ---------- the room (from the approved design sheet) ----------
  let RID=0;let DAY='pm';/* am / pm / eve: how full the machine is */
  const PILE={am:46,pm:96,eve:150};

  /* ---------- the room: warm cinema-red walls, wood panelling, two wall lamps ---------- */
  function room(W2,H2,win,lamps=true){const id='rm'+(RID++);const fl=H2*.86;
   let s=`<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1A0A0C"/><stop offset="1" stop-color="#2E1216"/></linearGradient><linearGradient id="${id}p" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2A1610"/><stop offset="1" stop-color="#170C08"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2A1A12"/><stop offset="1" stop-color="#0C0806"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".6"/></radialGradient><radialGradient id="${id}h"><stop offset="0" stop-color="#FFCF8A" stop-opacity=".55"/><stop offset=".4" stop-color="#FFB060" stop-opacity=".14"/><stop offset="1" stop-color="#FFB060" stop-opacity="0"/></radialGradient></defs>`;
   s+=`<rect width="${W2}" height="${H2}" fill="url(#${id}w)"/>`;
   /* soft velvet-like vertical folds in the wallpaper */
   for(let x=0;x<W2;x+=W2/32)s+=`<rect x="${f1(x)}" y="0" width="${f1(W2/64)}" height="${fl}" fill="#fff" opacity=".018"/>`;
   const wy=fl-H2*.2;s+=`<rect y="${f1(wy)}" width="${W2}" height="${f1(fl-wy)}" fill="url(#${id}p)"/><rect y="${f1(wy)}" width="${W2}" height="${f1(H2*.012)}" fill="#4A2A1A"/>`;
   for(let x=W2/24;x<W2;x+=W2/12)s+=`<rect x="${f1(x)}" y="${f1(wy+H2*.03)}" width="${f1(W2/14)}" height="${f1(fl-wy-H2*.05)}" rx="4" fill="none" stroke="#000" stroke-opacity=".3" stroke-width="2"/>`;
   if(win)s+=windowSVG(win);
   if(false&&lamps&&W2>600){[[700,300],[1460,300]].forEach(([x,y])=>{s+=`<circle class="lamph" cx="${x}" cy="${y}" r="190" fill="url(#${id}h)"/><path d="M${x-8} ${y+30} h16 v20 h-16Z" fill="#7A5A2A"/><path d="M${x-30} ${y+30} L${x-20} ${y-20} H${x+20} L${x+30} ${y+30}Z" fill="#E8C890"/><path d="M${x-30} ${y+30} L${x-20} ${y-20} H${x+20} L${x+30} ${y+30}Z" fill="#FFF0C8" opacity=".5"/>`})}
   s+=`<rect y="${fl}" width="${W2}" height="${H2-fl}" fill="url(#${id}f)"/>`;for(let k=1;k<5;k++)s+=`<path d="M0 ${f1(fl+(H2-fl)*k/5)} H${W2}" stroke="#000" stroke-opacity=".28" stroke-width="2"/>`;
   s+=`<rect y="${fl-8}" width="${W2}" height="8" fill="#120806"/>`;
   return {bg:s,vig:`<rect width="${W2}" height="${H2}" fill="url(#${id}v)"/>`,fl}}

  /* ---------- the window: a snowy street at dusk, a little cinema across the road with chasing bulbs ---------- */
  function windowSVG([wx,wy,ww,wh]){const id='wn'+(RID++);const r=rnd(41);const gy=wh-40;
   let s=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0A1028"/><stop offset=".7" stop-color="#2A2E58"/><stop offset="1" stop-color="#4A3A5E"/></linearGradient><clipPath id="${id}c"><rect width="${ww}" height="${wh}"/></clipPath><radialGradient id="${id}g"><stop offset="0" stop-color="#FFD98A" stop-opacity=".5"/><stop offset="1" stop-color="#FFD98A" stop-opacity="0"/></radialGradient></defs>`;
   let inner=`<rect width="${ww}" height="${wh}" fill="url(#${id}s)"/>`;
   for(let k=0;k<26;k++)inner+=`<circle cx="${f1(r()*ww)}" cy="${f1(r()*wh*.35)}" r="${f1(.6+r()*1.1)}" fill="#fff" opacity="${f1(.3+r()*.5)}"/>`;
   /* the cinema across the street */
   const bx=40,bw=ww-80,by=wh*.24;inner+=`<rect x="${bx}" y="${by}" width="${bw}" height="${gy-by-18}" fill="#3A1A20"/><path d="M${bx-6} ${by} H${bx+bw+6} L${bx+bw} ${by-16} H${bx}Z" fill="#2A1014"/>`;
   inner+=`<rect x="${bx+bw*.2}" y="${gy-90}" width="${bw*.6}" height="72" fill="#FFCF7A" opacity=".75"/><rect x="${bx+bw*.5-1}" y="${gy-90}" width="2" height="72" fill="#3A1A20"/>`;
   const mx=bx+12,my=by+18,mw=bw-24,mh=62;inner+=`<rect x="${mx}" y="${my}" width="${mw}" height="${mh}" rx="4" fill="#C21F27"/><rect x="${mx+10}" y="${my+12}" width="${mw-20}" height="${mh-24}" fill="#FFF6E2"/>`;
   inner+=`<text x="${mx+mw/2}" y="${my+mh/2+9}" font-family="Outfit,sans-serif" font-weight="800" font-size="${Math.min(25,mw/9.6)}" fill="#2A1014" text-anchor="middle">POPCORN NIGHT</text>`;
   const nb=Math.round((mw*2+mh*2)/14);for(let k=0;k<nb;k++){const t=k/nb*(2*mw+2*mh);let x,y;if(t<mw){x=mx+t;y=my+4}else if(t<mw+mh){x=mx+mw-4;y=my+t-mw}else if(t<2*mw+mh){x=mx+mw-(t-mw-mh);y=my+mh-4}else{x=mx+4;y=my+mh-(t-2*mw-mh)}
    inner+=`<circle class="mq" data-k="${k%3}" cx="${f1(x)}" cy="${f1(y)}" r="3.4" fill="#FFE9A8"/>`}
   inner+=`<rect x="${bx}" y="${my+mh+14}" width="${bw}" height="10" fill="#2A1014"/>`;
   /* snowy pavement and street lamp */
   inner+=`<rect y="${gy}" width="${ww}" height="${wh-gy}" fill="#D8DCEA"/><rect y="${gy+12}" width="${ww}" height="${wh-gy-12}" fill="#B8BED4"/><rect y="${gy-4}" width="${ww}" height="6" fill="#EEF1F8"/>`;
   inner+=`<rect x="${ww-26}" y="${gy-170}" width="5" height="170" fill="#14141C"/><circle cx="${ww-24}" cy="${gy-174}" r="40" fill="url(#${id}g)"/><path d="M${ww-36} ${gy-176} h24 l-4 -14 h-16Z" fill="#14141C"/><rect x="${ww-32}" y="${gy-176}" width="16" height="6" fill="#FFE9A8"/>`;
   inner+=`<g class="walkers" data-w="${ww}" data-gy="${gy}">${walkers()}</g>`;
   for(let k=0;k<40;k++)inner+=`<circle class="sn" data-x="${f1(r()*ww)}" data-y="${f1(r()*wh)}" data-v="${f1(10+r()*16)}" data-d="${f1(r()*6)}" data-wh="${wh}" r="${f1(1+r()*1.6)}" fill="#fff" opacity=".85"/>`;
   s+=`<g transform="translate(${wx} ${wy})"><rect x="-22" y="-22" width="${ww+44}" height="${wh+44}" rx="6" fill="#2A1610"/><rect x="-14" y="-14" width="${ww+28}" height="${wh+28}" fill="#3E2416"/><g clip-path="url(#${id}c)">${inner}</g>
   <rect x="${ww/2-5}" y="0" width="10" height="${wh}" fill="#3E2416"/><rect x="0" y="${wh*.42-5}" width="${ww}" height="10" fill="#3E2416"/>
   <path d="M10 10 L${ww*.3} 10 L10 ${wh*.3}Z" fill="#fff" opacity=".05"/>
   <rect x="-30" y="${wh+14}" width="${ww+60}" height="16" rx="3" fill="#4A2A1A"/></g>`;
   return s}

  /* ---------- people walking past: legs bend at the knee and feet stay planted on the pavement ---------- */
  const SK=['#E8B48A','#B97E58','#8A5A3A','#F2C9A4'];
  /* a leg: hip at (hx,hy) in the walker's own space; ph = where in its step this leg starts; a,b = thigh, shin */
  const legEl=(hx,hy,a,b,ph,col,shoe,w=6.5)=>`<g class="lg" data-hx="${hx}" data-hy="${hy}" data-a="${a}" data-b="${b}" data-ph="${ph}" data-w="${w}"><path class="lp" d="" stroke="${col}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" fill="none"/><path class="sh" d="" fill="${shoe}"/></g>`;
  const armEl=(sx,sy,len,ph,col,hand,w=6)=>`<g class="arm" data-sx="${sx}" data-sy="${sy}" data-l="${len}" data-ph="${ph}"><path class="ap" d="" stroke="${col}" stroke-width="${w}" stroke-linecap="round" fill="none"/><circle class="hd" r="3.6" fill="${hand}"/></g>`;
  function head(x,y,r,hair,sk,o={}){let s=`<circle cx="${x}" cy="${y}" r="${r}" fill="${sk}"/>`;
   if(hair)s+=`<path d="M${x-r} ${y-r*.05} C${x-r*1.05} ${y-r*1.35} ${x+r*1.15} ${y-r*1.4} ${x+r*1.02} ${y+r*.25} C${x+r*.7} ${y-r*.2} ${x+r*.1} ${y-r*.5} ${x-r} ${y-r*.05}Z" fill="${hair}"/>`;
   s+=`<circle cx="${x-r*.5}" cy="${y-r*.05}" r="${r*.13}" fill="#2A1A10"/><path d="M${x-r*.68} ${y+r*.38} q${r*.22} ${r*.18} ${r*.44} 0" stroke="#7A3A20" stroke-width="1.3" fill="none" stroke-linecap="round"/><circle cx="${x-r*.2}" cy="${y+r*.25}" r="${r*.2}" fill="#E07A6A" opacity=".4"/>`;
   if(o.hat)s+=o.hat;return s}
  function wKid(){/* a kid hugging a huge striped popcorn bucket */
   const sk=SK[3];let s=legEl(-3,-34,17,17,0,'#2E3A5A','#4A2A18',6)+legEl(4,-34,17,17,.5,'#2E3A5A','#4A2A18',6);
   s+=`<rect x="-12" y="-68" width="24" height="38" rx="9" fill="#E8902A"/>`;
   s+=head(0,-80,11,'#5A3418',sk,{hat:`<path d="M-11 -82 C-11 -98 11 -98 11 -82Z" fill="#3A6AB8"/><rect x="-12" y="-84" width="24" height="5" rx="2.5" fill="#fff"/><circle cx="0" cy="-96" r="4.5" fill="#fff"/>`});
   s+=`<g transform="translate(-16 -36) scale(.5)">${bucket('butter',33)}</g>`;
   s+=`<path d="M-4 -60 Q-14 -54 -22 -58" stroke="#E8902A" stroke-width="6.5" fill="none" stroke-linecap="round"/><circle cx="-23" cy="-58" r="3.6" fill="${sk}"/>`;
   return `<g class="wk" data-off="0" data-s="1.6" data-step="11">${s}</g>`}
  function wCouple(){/* two grown-ups sharing a paper bag of popcorn; the one behind keeps reaching into it */
   const sk1=SK[0],sk2=SK[2];
   let s=legEl(14,-46,24,24,.25,'#1E2440','#15121A')+legEl(22,-46,24,24,.75,'#1E2440','#15121A');
   s+=`<path d="M8 -96 h28 l5 54 h-38Z" fill="#2E5A4A"/><rect x="10" y="-98" width="24" height="7" rx="3.5" fill="#E8C870"/>`+head(22,-108,11,'#1A1410',sk2);
   s+=legEl(-20,-46,24,24,0,'#2A2A3A','#15121A')+legEl(-12,-46,24,24,.5,'#2A2A3A','#15121A');
   s+=`<path d="M-26 -96 h28 l5 54 h-38Z" fill="#8A2A3A"/><rect x="-24" y="-98" width="24" height="7" rx="3.5" fill="#F4F0E6"/>`+head(-12,-108,11,'#C8843A',sk1);
   /* the bag, held in front by the first person */
   s+=`<g transform="translate(-34 -60) scale(.42)">${microBag('butter',44)}</g><path d="M-18 -86 L-32 -66" stroke="#8A2A3A" stroke-width="7" stroke-linecap="round"/><circle cx="-33" cy="-64" r="3.6" fill="${sk1}"/>`;
   s+=`<path class="reach" d="" stroke="#2E5A4A" stroke-width="7" stroke-linecap="round" fill="none"/><circle class="reachh" r="3.6" fill="${sk2}"/>`;
   return `<g class="wk" data-off="8" data-s="1.45" data-step="14">${s}</g>`}
  function wDog(){/* someone walking a small dog on a lead */
   const sk=SK[1];let s='';
   /* the dog, ahead of the walker */
   const dx=-58;s+=legEl(dx+12,-16,8,8,.5,'#C88A4A','#6A3A1A',4.5)+legEl(dx-8,-16,8,8,0,'#C88A4A','#6A3A1A',4.5);
   s+=`<ellipse cx="${dx+2}" cy="-20" rx="16" ry="8" fill="#D9A060"/><path class="tail" d="" stroke="#D9A060" stroke-width="4" stroke-linecap="round" fill="none"/>`;
   s+=`<circle cx="${dx-16}" cy="-30" r="8" fill="#D9A060"/><ellipse cx="${dx-23}" cy="-28" rx="5" ry="3.4" fill="#E8B880"/><circle cx="${dx-27}" cy="-29" r="1.8" fill="#2A1A10"/><circle cx="${dx-17}" cy="-33" r="1.4" fill="#2A1A10"/><path d="M${dx-12} -36 q4 6 1 12" stroke="#8A5A2A" stroke-width="4" fill="none" stroke-linecap="round"/><rect x="${dx-14}" y="-25" width="7" height="3" fill="#C21F27"/>`;
   s+=legEl(dx+10,-16,8,8,0,'#B87A3A','#5A2A10',4.5)+legEl(dx-10,-16,8,8,.5,'#B87A3A','#5A2A10',4.5);
   /* the walker */
   s+=legEl(-3,-46,24,24,0,'#3A2A20','#15121A')+legEl(5,-46,24,24,.5,'#3A2A20','#15121A');
   s+=`<path d="M-13 -96 h28 l5 54 h-38Z" fill="#5A3A6A"/><rect x="-11" y="-98" width="24" height="7" rx="3.5" fill="#E8B84A"/>`+head(1,-108,11,'#2A1A10',sk,{hat:`<path d="M-10 -112 C-10 -126 12 -126 12 -112Z" fill="#C21F27"/><circle cx="1" cy="-126" r="4" fill="#fff"/>`});
   s+=`<path d="M-6 -88 L-16 -66" stroke="#5A3A6A" stroke-width="7" stroke-linecap="round"/><circle cx="-17" cy="-64" r="3.6" fill="${sk}"/><path class="leash" d="M-17 -64 Q-40 -40 ${dx-10} -24" stroke="#C21F27" stroke-width="1.6" fill="none"/>`;
   return `<g class="wk" data-off="16" data-s="1.45" data-step="12">${s}</g>`}
  function wVendor(){/* a popcorn seller pushing a little cart, wheels turning */
   const sk=SK[0];let s='';
   s+=`<g transform="translate(-48 -14) scale(.62)">${machine('butter',55,{still:true,full:true})}</g>`;
   s+=`<g class="whl" data-x="-48" data-y="-8"><circle cx="-48" cy="-8" r="9" fill="#1A1416" stroke="#C9A64A" stroke-width="2.4"/><path d="M-57 -8 H-39 M-48 -17 V1" stroke="#C9A64A" stroke-width="1.6"/></g>`;
   s+=legEl(8,-46,24,24,0,'#2A2A3A','#15121A')+legEl(16,-46,24,24,.5,'#2A2A3A','#15121A');
   s+=`<path d="M2 -96 h28 l5 54 h-38Z" fill="#F4F0E6"/><path d="M2 -96 h28 l1 12 h-30Z" fill="#C21F27"/>`+head(16,-108,11,'#6A4A2A',sk,{hat:`<rect x="5" y="-128" width="22" height="12" rx="2" fill="#fff"/><rect x="5" y="-120" width="22" height="4" fill="#C21F27"/>`});
   s+=`<path d="M8 -86 L-14 -66" stroke="#F4F0E6" stroke-width="7" stroke-linecap="round"/><circle cx="-15" cy="-65" r="3.6" fill="${sk}"/><path d="M-26 -66 H-12" stroke="#C9A64A" stroke-width="3"/>`;
   return `<g class="wk" data-off="24" data-s="1.4" data-step="12">${s}</g>`}
  function walkers(){return wKid()+wCouple()+wDog()+wVendor()}

  /* ---------- the big popcorn machine ---------- */
  /* Built standing on y=0, centred on x=0, about 560 tall. The case is glass; popcorn lies on its floor (y=CASEF). */
  const CASEF=-250,CASET=-500,CW=150,KX=0,KY=-428;
  function surfY(x,h){const t=Math.min(1,Math.abs(x)/(CW-6));return CASEF-h*Math.max(0,1-t*t*.8)}
  function bigMachine(h){const p='bm'+(RID++);let s=`<defs>
   <linearGradient id="${p}r" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#5A0A10"/><stop offset=".25" stop-color="#C21F27"/><stop offset=".45" stop-color="#E8463E"/><stop offset=".7" stop-color="#B01A22"/><stop offset="1" stop-color="#4A080C"/></linearGradient>
   <linearGradient id="${p}g" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#7A5410"/><stop offset=".35" stop-color="#F6DA86"/><stop offset=".6" stop-color="#D9A940"/><stop offset="1" stop-color="#6A4A0C"/></linearGradient>
   <radialGradient id="${p}l" cx=".5" cy=".12" r=".95"><stop offset="0" stop-color="#FFE3A0" stop-opacity=".75"/><stop offset=".5" stop-color="#E89A40" stop-opacity=".22"/><stop offset="1" stop-color="#3A1A10" stop-opacity="0"/></radialGradient>
   <radialGradient id="${p}hl"><stop offset="0" stop-color="#FFE7A8" stop-opacity=".5"/><stop offset="1" stop-color="#FFE7A8" stop-opacity="0"/></radialGradient>
   <clipPath id="${p}c"><rect x="${-CW}" y="${CASET}" width="${2*CW}" height="${CASEF-CASET}"/></clipPath></defs>`;
   s+=`<circle class="mglow" cx="0" cy="${(CASET+CASEF)/2}" r="330" fill="url(#${p}hl)"/>`;
   s+=`<ellipse cx="0" cy="4" rx="${CW+60}" ry="18" fill="#000" opacity=".45"/>`;
   /* cart body */
   s+=`<rect x="${-CW}" y="-236" width="${2*CW}" height="200" rx="8" fill="url(#${p}r)"/><rect x="${-CW+18}" y="-214" width="${2*CW-36}" height="156" rx="8" fill="none" stroke="url(#${p}g)" stroke-width="5"/>`;
   s+=`<rect x="${-CW+34}" y="-198" width="${2*CW-68}" height="124" rx="6" fill="#000" opacity=".12"/><circle cx="${CW-52}" cy="-136" r="7" fill="url(#${p}g)"/>`;
   s+=`<text x="0" y="-122" font-family="Outfit,sans-serif" font-weight="800" font-size="28" fill="#F6C744" text-anchor="middle" letter-spacing="1">HOT &amp; FRESH</text>`;
   /* push handle and wheels */
   s+=`<path d="M${CW} -200 h44 v-60" stroke="url(#${p}g)" stroke-width="9" fill="none" stroke-linecap="round"/><rect x="${CW+30}" y="-272" width="28" height="18" rx="6" fill="#2A1A14"/>`;
   [-CW+38,CW-38].forEach(x=>{s+=`<circle cx="${x}" cy="-38" r="38" fill="#1A1214" stroke="url(#${p}g)" stroke-width="8"/>`;for(let k=0;k<8;k++){const a=k/8*Math.PI*2;s+=`<path d="M${x} -38 L${f1(x+Math.cos(a)*32)} ${f1(-38+Math.sin(a)*32)}" stroke="#C9A64A" stroke-width="3"/>`}s+=`<circle cx="${x}" cy="-38" r="8" fill="url(#${p}g)"/>`});
   /* counter ledge */
   s+=`<rect x="${-CW-12}" y="-252" width="${2*CW+24}" height="18" rx="4" fill="url(#${p}g)"/>`;
   /* inside the glass case: warm back wall, the pile, the kettle, flying pieces */
   s+=`<rect x="${-CW}" y="${CASET}" width="${2*CW}" height="${CASEF-CASET}" fill="#2A1410"/><rect class="caselight" x="${-CW}" y="${CASET}" width="${2*CW}" height="${CASEF-CASET}" fill="url(#${p}l)"/>`;
   s+=`<g clip-path="url(#${p}c)">`;
   s+=`<g class="heap">${pile(0,CASEF,2*CW-10,h,0,'butter',101,26)}</g>`;
   /* the kettle hangs from a steel rod; it tips over on the pivot at its right side */
   s+=`<rect x="${KX+51}" y="${CASET}" width="10" height="${KY-24-CASET}" fill="url(#pg_steel)"/><circle cx="${KX+56}" cy="${KY-24}" r="9" fill="#8A93A0" stroke="#4A5260" stroke-width="2"/>`;
   s+=`<g class="kettle" data-px="${KX+56}" data-py="${KY-24}">
    <path d="M${KX-64} ${KY-34} H${KX+64} L${KX+58} ${KY+24} Q${KX} ${KY+44} ${KX-58} ${KY+24}Z" fill="url(#pg_steel)" stroke="#5A6474" stroke-width="2"/>
    <path d="M${KX-64} ${KY-34} H${KX+64}" stroke="#E4E9F0" stroke-width="6" stroke-linecap="round"/>
    <path class="lidL" d="M${KX-62} ${KY-36} Q${KX-31} ${KY-60} ${KX} ${KY-36}Z" fill="#C6CED9" stroke="#5A6474" stroke-width="2"/>
    <path class="lidR" d="M${KX} ${KY-36} Q${KX+31} ${KY-60} ${KX+62} ${KY-36}Z" fill="#B8C2D0" stroke="#5A6474" stroke-width="2"/>
    <path d="M${KX-48} ${KY-24} Q${KX-50} ${KY+10} ${KX-36} ${KY+28}" stroke="#fff" stroke-width="7" opacity=".55" fill="none" stroke-linecap="round"/>
    <rect x="${KX-70}" y="${KY-2}" width="10" height="16" rx="3" fill="#2A1A14"/></g><circle cx="${KX+56}" cy="${KY-24}" r="6" fill="#C6CED9" stroke="#4A5260" stroke-width="2"/>`;
   s+=`<g class="pops">${Array.from({length:22},(_,k)=>`<g class="pp" data-k="${k}" opacity="0">${puff(0,0,24,'butter',300+k)}</g>`).join('')}</g>`;
   s+=`<g class="pour">${Array.from({length:30},(_,k)=>`<g class="pq" data-k="${k}" opacity="0">${puff(0,0,24,'butter',400+k)}</g>`).join('')}</g>`;
   s+=`</g>`;
   /* glass: shine streaks */
   s+=`<path d="M${-CW+16} ${CASET+14} L${-CW+70} ${CASET+14} L${-CW+16} ${CASET+130}Z" fill="#fff" opacity=".09"/><path d="M${CW-60} ${CASEF-12} L${CW-14} ${CASEF-90} L${CW-14} ${CASEF-12}Z" fill="#fff" opacity=".06"/>`;
   /* gold posts and a little door on the front of the glass */
   [-CW,CW].forEach(x=>{s+=`<rect x="${x-7}" y="${CASET}" width="14" height="${CASEF-CASET}" fill="url(#${p}g)"/>`});
   s+=`<rect x="${-CW}" y="${CASEF-6}" width="${2*CW}" height="8" fill="url(#${p}g)"/><rect x="${-CW}" y="${CASET-2}" width="${2*CW}" height="8" fill="url(#${p}g)"/>`;

   /* roof, sign and lamp */
   s+=`<path d="M${-CW-18} ${CASET} L${-CW+6} ${CASET-58} H${CW-6} L${CW+18} ${CASET}Z" fill="url(#${p}r)"/><rect x="${-CW-18}" y="${CASET-6}" width="${2*CW+36}" height="10" rx="3" fill="url(#${p}g)"/>`;
   s+=`<rect x="-120" y="${CASET-130}" width="240" height="70" rx="12" fill="#C21F27" stroke="url(#${p}g)" stroke-width="6"/><text x="0" y="${CASET-82}" font-family="'Berkshire Swash',serif" font-size="42" fill="#FFE9A8" text-anchor="middle">Popcorn</text>`;
   for(let k=0;k<11;k++){const x=-108+k*21.6;s+=`<circle class="sb" data-k="${k%2}" cx="${f1(x)}" cy="${CASET-136}" r="5" fill="#FFE9A8"/>`}
   s+=`<circle cx="0" cy="${CASET-150}" r="12" fill="url(#${p}g)"/><path d="M0 ${CASET-162} v-14" stroke="url(#${p}g)" stroke-width="5" stroke-linecap="round"/><circle cx="0" cy="${CASET-180}" r="7" fill="url(#${p}g)"/>`;
   /* the little lamp inside the roof */
   s+=`<circle class="bulb" cx="0" cy="${CASET+18}" r="11" fill="#FFF3C8"/>`;
   return s}
  function floorBits(h){/* the striped bucket on the floor beside the machine, and a few pieces that missed */
   let s=`<g transform="translate(250 0) scale(1.7)">${bucket('butter',61)}</g>`;
   if(DAY!=='am')s+=puff(196,-8,22,'butter',71)+puff(320,-6,20,'butter',72)+kernel(350,-6,12,40);
   if(DAY==='eve')s+=`<g transform="translate(372 0) scale(1.15)">${bowl('butter',73)}</g>`;
   return s}
  function scene(sc){return `<g class="mscene" data-h="${PILE[DAY]}">${bigMachine(PILE[DAY])}${floorBits()}</g>`}

  // walking: where each foot is in its step, and the knee that joins hip and foot
  function legPose(ph,L,lift){ph=((ph%1)+1)%1;if(ph<.5){const e=ph/.5;return [-L+2*L*e,0]}const e=(ph-.5)/.5;const ee=e*e*(3-2*e);return [L-2*L*ee,-lift*Math.sin(Math.PI*e)]}
  function ik(hx,hy,fx,fy,a,b,fwd){let dx=fx-hx,dy=fy-hy;let d=Math.hypot(dx,dy);const m=a+b-.01;if(d>m){fx=hx+dx/d*m;fy=hy+dy/d*m;dx=fx-hx;dy=fy-hy;d=m}
   const th=Math.atan2(dy,dx);const al=Math.acos(Math.max(-1,Math.min(1,(a*a+d*d-b*b)/(2*a*d))));
   const k1=[hx+a*Math.cos(th+al),hy+a*Math.sin(th+al)],k2=[hx+a*Math.cos(th-al),hy+a*Math.sin(th-al)];
   return [(fwd<0?(k1[0]<k2[0]?k1:k2):(k1[0]>k2[0]?k1:k2)),[fx,fy]]}

  /** The two shapes: wide (TVs, computers) and tall (phones, no window like the other holiday rooms) */
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1572, 150, 300, 430], cart: [290, 26, 0.84] },
    tall: { w: 390, h: 844, win: null, cart: [110, 8, 0.5] },
  };
  /** the fonts the room's signs are written in (the web's own font names first) */
  const fixFonts = (s) => s.replace(/font-family="'Berkshire Swash',serif"/g, `font-family="'Glowfin Xmas','Berkshire Swash',serif"`);

  /**
   * Builds the room into an <svg> element. Returns { at(t) } which poses everything at t seconds.
   * opts: { layout: "wide"|"tall", day: "am"|"pm"|"eve" (default: now), align }
   */
  function build(svg, opts) {
    const L = LAYOUTS[(opts && opts.layout) || "wide"];
    DAY = (opts && opts.day) || variant(new Date());
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", (opts && opts.align) || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win, false);
    svg.innerHTML = fixFonts(GDEF + rm.bg + `<g transform="translate(${L.cart[0]} ${rm.fl + L.cart[1]}) scale(${L.cart[2]})">${scene()}</g>` + rm.vig);
    return pose(svg);
  }

  /* gathers every moving part, and returns at(t). The same moves as the design sheet, with every speed
     rounded so it repeats exactly once a loop. */
  function pose(root) {
    const q = (s) => [...root.querySelectorAll(s)];
    const W = q(".walkers").map((w) => ({ ww: +w.dataset.w, gy: +w.dataset.gy, wk: [...w.querySelectorAll(".wk")].map((g) => ({ g, off: +g.dataset.off, sc: +g.dataset.s, L: +g.dataset.step, legs: [...g.querySelectorAll(".lg")].map((l) => ({ l, hx: +l.dataset.hx, hy: +l.dataset.hy, a: +l.dataset.a, b: +l.dataset.b, ph: +l.dataset.ph, w: +l.dataset.w, p: l.querySelector(".lp"), s: l.querySelector(".sh") })), reach: g.querySelector(".reach"), rh: g.querySelector(".reachh"), tail: g.querySelector(".tail"), whl: g.querySelector(".whl") })) }));
    const snow = q(".sn").map((c) => { const H = +c.dataset.wh + 10; const v0 = +c.dataset.v; return { c, x: +c.dataset.x, y: +c.dataset.y, v: Math.max(1, Math.round(v0 * LOOP / H)) * H / LOOP, d: +c.dataset.d, H }; });
    const mq = q(".mq,.sb"); const lamps = q(".lamph,.mglow,.caselight");
    const M = q(".mscene").map((m) => ({ h: +m.dataset.h, k: m.querySelector(".kettle"), pp: [...m.querySelectorAll(".pp")], pq: [...m.querySelectorAll(".pq")], lidL: m.querySelector(".lidL"), lidR: m.querySelector(".lidR") }));
    const R = rnd(9);
    const PP = M.length ? M[0].pp.map(() => ({ per: pq(1.5 + R() * 1.4), off: R() * 3, x0: (R() - 0.5) * 90, vx: (R() - 0.5) * 170, vy: -(140 + R() * 140) })) : [];
    const PQ = M.length ? M[0].pq.map((_, k) => ({ dl: k * 0.045, vx: -(20 + R() * 90), vy: -(10 + R() * 60), dx: (R() - 0.5) * 14 })) : [];
    const TIP = 12, GR = 520;
    const STEP = pq(1.05), s08 = wq(0.8), s17 = wq(1.7), s06 = wq(0.6), s9 = wq(9), s16 = wq(1.6);
    function at(t) {
      t = ((t % LOOP) + LOOP) % LOOP;
      snow.forEach((S) => { const yy = ((S.y + t * S.v) % S.H) - 5; S.c.setAttribute("cx", f1(S.x + Math.sin(t * s08 + S.d) * 6)); S.c.setAttribute("cy", f1(yy)); });
      mq.forEach((b) => { const on = (Math.floor(t * 4) + (+b.dataset.k)) % (b.classList.contains("sb") ? 2 : 3) === 0; b.setAttribute("opacity", on ? "1" : ".35"); });
      lamps.forEach((l, i) => l.setAttribute("opacity", (0.88 + 0.12 * Math.sin(t * s17 + i) * Math.sin(t * s06 + i * 2)).toFixed(3)));
      W.forEach((Wn) => Wn.wk.forEach((w) => {
        const tt = (t + LOOP - w.off) % LOOP; const v = 2 * w.L / (STEP / 2) * w.sc; const x = Wn.ww + 140 - tt * v; const cyc = tt / STEP;
        const bob = Math.abs(Math.sin(cyc * Math.PI * 2)) * 1.4;
        w.g.setAttribute("transform", `translate(${f1(x)} ${f1(Wn.gy + 2 - bob)}) scale(${w.sc})`);
        w.legs.forEach((Lg) => {
          const [fx, fy] = legPose(cyc + Lg.ph, w.L * (Lg.a < 12 ? 0.55 : 1), Lg.a < 12 ? 5 : 9);
          const [kn, ft] = ik(Lg.hx, Lg.hy, Lg.hx + fx, fy - 1, Lg.a, Lg.b, Lg.a < 12 ? 1 : -1);
          Lg.p.setAttribute("d", `M${f1(Lg.hx)} ${f1(Lg.hy)}L${f1(kn[0])} ${f1(kn[1])}L${f1(ft[0])} ${f1(ft[1])}`); const sw = Lg.w * 1.35;
          Lg.s.setAttribute("d", `M${f1(ft[0] + sw * 0.5)} ${f1(ft[1] + 1.5)} h${f1(-sw * 1.9)} a${f1(sw * 0.5)} ${f1(sw * 0.5)} 0 0 1 ${f1(sw * 0.3)} ${f1(-sw * 0.8)} h${f1(sw * 1.6)}Z`);
        });
        if (w.reach) { const e = 0.5 - 0.5 * Math.cos(t * s16); const hx2 = -26 + e * -4, hy2 = -70 - e * 8; w.reach.setAttribute("d", `M14 -88 Q-2 ${f1(-84 - e * 4)} ${f1(hx2)} ${f1(hy2)}`); w.rh.setAttribute("cx", f1(hx2)); w.rh.setAttribute("cy", f1(hy2)); }
        if (w.tail) { const a = Math.sin(t * s9) * 0.6; const bx = -58 + 18, by = -22; w.tail.setAttribute("d", `M${bx} ${by} q${f1(8 + Math.sin(a) * 4)} ${f1(-6 - Math.cos(a) * 4)} ${f1(10 + Math.sin(a) * 8)} ${f1(-14 + Math.abs(a) * 4)}`); }
        if (w.whl) w.whl.setAttribute("transform", `rotate(${f1(-(tt * v / w.sc) / 9 * 57.3)} ${w.whl.dataset.x} ${w.whl.dataset.y})`);
      }));
      M.forEach((m) => {
        const tc = t % TIP; let ang = 0;
        if (tc < 2.6) { const e = tc < 0.5 ? tc / 0.5 : tc < 1.9 ? 1 : 1 - (tc - 1.9) / 0.7; ang = -58 * (e * e * (3 - 2 * e)); }
        m.k.setAttribute("transform", `rotate(${f1(ang)} ${m.k.dataset.px} ${m.k.dataset.py})`);
        const lidUp = Math.max(0, Math.sin(t * s9)) * 6;
        m.lidL.setAttribute("transform", `rotate(${f1(-lidUp)} ${KX - 62} ${KY - 36})`); m.lidR.setAttribute("transform", `rotate(${f1(lidUp * 0.8)} ${KX + 62} ${KY - 36})`);
        // single pieces popping out of the open lid, arcing and landing on the heap
        m.pp.forEach((g, k) => {
          const P = PP[k]; if (tc < 2.7) { g.setAttribute("opacity", "0"); return; } const tt = (t + P.off) % P.per;
          let x = KX + P.x0 * 0.6 + P.vx * tt, y = KY - 40 + P.vy * tt + 0.5 * GR * tt * tt; if (x > CW - 12) x = 2 * (CW - 12) - x; if (x < -CW + 12) x = 2 * (-CW + 12) - x;
          const sy = surfY(x, m.h) + 10; let op = 1; if (y > sy) { y = sy; op = Math.max(0, 1 - (tt - 0.9) * 1.4); }
          g.setAttribute("transform", `translate(${f1(x)} ${f1(y)}) rotate(${f1(tt * 240 * (k % 2 ? 1 : -1))})`); g.setAttribute("opacity", f1(Math.min(1, op)));
        });
        // when the kettle tips, a whole batch pours out of its lip
        const pv = KX + 56, pyv = KY - 24; const la = ang * Math.PI / 180;
        const lx = pv + (KX - 64 - pv) * Math.cos(la) - (KY - 30 - pyv) * Math.sin(la), ly = pyv + (KX - 64 - pv) * Math.sin(la) + (KY - 30 - pyv) * Math.cos(la);
        m.pq.forEach((g, k) => {
          const P = PQ[k]; const tt = tc - 0.45 - P.dl; if (tt < 0) { g.setAttribute("opacity", "0"); return; }
          let x = lx + P.dx + P.vx * tt, y = ly + P.vy * tt + 0.5 * GR * tt * tt; if (x < -CW + 12) x = -CW + 12; const sy = surfY(x, m.h) + 8; let op = 1;
          if (y > sy) { y = sy; op = tc > TIP - 1.6 ? Math.max(0, (TIP - tc) / 1.6) : 1; }
          g.setAttribute("transform", `translate(${f1(x)} ${f1(y)})`); g.setAttribute("opacity", f1(op));
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

  /** the drawing kit, used by tools/popcorn/pictures.js to draw the door snacks and small touches for the app and web */
  const kit = { GDEF, puff, kernel, bucket, microBag, machine, bowl, tin, theatreBox, ticket, reel };
  G.BenflixPopcorn = { LOOP, season, greeting, line, variant, build, play, LAYOUTS, kit };
})(typeof window !== "undefined" ? window : globalThis);
