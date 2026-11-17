/* Benflix Friday the 13th: the Who's watching room (approved 2026-11-17, thread "Design a Friday the 13th theme for Benflix").
 *
 * A dim, cosy plum room lit by warm globe bulbs that flicker twice together every 15 seconds, like a storm
 * nearby. Bottom left is a small round table with a velvet cloth: a crystal ball with mist swirling inside, a
 * dripping candle with a real flame and a tipped-over salt shaker. On the wall, a calendar open on Friday the
 * 13th and a horseshoe hung the lucky way up. On the floor a black cat sleeps on a red cushion, breathing, with
 * z's floating up and an ear that twitches. When someone picks a profile the cat opens its eyes and yawns.
 * Through the window: a moonlit lake in the pines, mist, fireflies and bats, with a black cat, a kid in a yellow
 * raincoat with a torch, someone under a big umbrella and a camper with a lantern walking past.
 * Only on a Friday the 13th, midnight to midnight, and it takes over any season look that day.
 *
 * Like xmas.js and feast.js, the same drawing is used live on the web and by tools/f13/record.js, which records it
 * as looping videos for the TV and phone app (plus a short clip of the cat waking up). Everything is a function of
 * the time t (seconds) and repeats every LOOP seconds, so a video of one loop joins up with no jump.
 * The drawing code comes straight from the approved design sheet (friday13-2-whos-watching.html).
 */
(function (G) {
  "use strict";
  const LOOP = 30;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;
  /** how long the cat takes to wake up and yawn after a profile is picked */
  const WAKE = 1.6;

  // ---------- dates ----------
  /** Friday the 13th, from midnight to midnight */
  const season = (d) => d.getDay() === 5 && d.getDate() === 13;
  const greeting = () => "Happy Friday the 13th";
  const line = () => "Pick a profile… if you dare";

  // ---------- drawing kit (from the approved design sheet) ----------
  let UID=0;const u=p=>p+(UID++)+'_';
  const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
  const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
  const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
  const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
  const f1=v=>(+v).toFixed(1);
  const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
  const T=(x,y,s,txt,o={})=>`<text x="${x}" y="${y}" font-family="${o.font||'Inter,sans-serif'}" font-weight="${o.w||600}" font-size="${s}" fill="${o.fill||'#F5F0EA'}" ${o.anchor?`text-anchor="${o.anchor}"`:''} ${o.sh?'filter="url(#Gts)"':''} ${o.op?`opacity="${o.op}"`:''}>${txt}</text>`;
  const PLUM='#2A2238',GREEN='#A6E36A';
  function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
  function smooth(P,closed){const n=P.length;let d=`M${f1(P[0][0])} ${f1(P[0][1])}`;const g=i=>closed?P[(i+n)%n]:P[Math.max(0,Math.min(n-1,i))];
   for(let i=0;i<(closed?n:n-1);i++){const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);d+=`C${f1(p1[0]+(p2[0]-p0[0])/6)} ${f1(p1[1]+(p2[1]-p0[1])/6)} ${f1(p2[0]-(p3[0]-p1[0])/6)} ${f1(p2[1]-(p3[1]-p1[1])/6)} ${f1(p2[0])} ${f1(p2[1])}`}return d+(closed?'Z':'')}
  function flameD(bx,by,w,h,t,ph,sp=1){const P=[];const N=7;
   for(let i=0;i<=N;i++){const s=i/N;const wob=Math.sin(t*wq(5.2*sp)+ph+s*3.1)*w*.16*s;const hw=w/2*Math.pow(1-s,.85)*(1+.25*Math.sin(s*Math.PI));P.push([bx-hw+wob,by-s*h])}
   const tipx=bx+Math.sin(t*wq(4.1*sp)+ph)*w*.2,tipy=by-h*(1.04+.08*Math.sin(t*wq(6.3*sp)+ph*2));
   const R=[];for(let i=N;i>=0;i--){const s=i/N;const wob=Math.sin(t*wq(5.2*sp)+ph+s*3.1)*w*.16*s;const hw=w/2*Math.pow(1-s,.85)*(1+.25*Math.sin(s*Math.PI));R.push([bx+hw+wob,by-s*h])}
   return smooth([...P.slice(0,N),[tipx,tipy],...R.slice(1)],true)}
  let RID=0;
  function groundY(x,ww,wh){return wh-38+Math.sin(x*.025)*3}

  /* ---------- the room: dusky plum walls, warm bulbs that flicker now and then ---------- */
  function room(W2,H2,win,lights=true){const id='rm'+(RID++);const fl=H2*.8;
   let s=`<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0E0B16"/><stop offset="1" stop-color="#1B1626"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1E1712"/><stop offset="1" stop-color="#0A0807"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".6"/></radialGradient><radialGradient id="${id}h"><stop offset="0" stop-color="#FFD58A" stop-opacity=".9"/><stop offset=".35" stop-color="#FFC060" stop-opacity=".25"/><stop offset="1" stop-color="#FFC060" stop-opacity="0"/></radialGradient></defs>`;
   s+=`<rect width="${W2}" height="${H2}" fill="url(#${id}w)"/>`;for(let x=0;x<W2;x+=W2/24)s+=`<rect x="${f1(x)}" y="0" width="${f1(W2/48)}" height="${fl}" fill="#fff" opacity=".014"/>`;
   s+=`<rect y="${f1(fl-H2*.16)}" width="${W2}" height="${f1(H2*.16)}" fill="#000" opacity=".2"/><rect y="${f1(fl-H2*.16)}" width="${W2}" height="4" fill="#3A2F4A" opacity=".8"/>`;
   if(win)s+=windowSVG(win);
   s+=`<rect y="${fl}" width="${W2}" height="${H2-fl}" fill="url(#${id}f)"/>`;for(let k=1;k<6;k++)s+=`<path d="M0 ${f1(fl+(H2-fl)*k/6)} H${W2}" stroke="#000" stroke-opacity=".25" stroke-width="2"/>`;
   s+=`<rect y="${fl-10}" width="${W2}" height="10" fill="#120E18"/>`;
   if(lights){const sc=W2/1920;const swags=W2<600?[[0,22,W2,22,22]]:[[300*sc,36*sc,800*sc,36*sc,96*sc],[800*sc,36*sc,1300*sc,36*sc,96*sc]];
    swags.forEach(([x1,y1,x2,y2,sag])=>{const N=Math.round((x2-x1)/(W2<600?34:48*sc));const pt=k=>{const t=k/N;return [x1+(x2-x1)*t,y1+(y2-y1)*t+sag*4*t*(1-t)]};
     let wire='';for(let k=0;k<=N;k++){const [x,y]=pt(k);wire+=(k?'L':'M')+f1(x)+' '+f1(y)}s+=`<path d="${wire}" stroke="#1A1A14" stroke-width="${f1(3*Math.max(sc,.5))}" fill="none"/>`;
     for(let k=1;k<N;k++){const [x,y]=pt(k);const r=(W2<600?7:11*sc);s+=`<rect x="${f1(x-r*.35)}" y="${f1(y)}" width="${f1(r*.7)}" height="${f1(r*.6)}" fill="#3A3226"/><g class="bulb" data-k="${k}"><circle cx="${f1(x)}" cy="${f1(y+r*1.6)}" r="${f1(r*3.2)}" fill="url(#${id}h)"/><circle cx="${f1(x)}" cy="${f1(y+r*1.6)}" r="${f1(r)}" fill="#FFE8B8"/><circle cx="${f1(x-r*.3)}" cy="${f1(y+r*1.3)}" r="${f1(r*.3)}" fill="#fff"/></g>`}})}
   return {bg:s,vig:`<rect width="${W2}" height="${H2}" fill="url(#${id}v)"/>`}}

  /* ---------- the window: a moonlit lake in the woods, mist, fireflies, bats ---------- */
  function pine(x,b,h,c){return `<path d="M${f1(x)} ${f1(b-h)} l${f1(-h*.22)} ${f1(h*.4)} h${f1(h*.08)} l${f1(-h*.16)} ${f1(h*.32)} h${f1(h*.1)} l${f1(-h*.14)} ${f1(h*.28)} h${f1(h*.68)} l${f1(-h*.14)} ${f1(-h*.28)} h${f1(h*.1)} l${f1(-h*.16)} ${f1(-h*.32)} h${f1(h*.08)}Z" fill="${c}"/>`}
  function windowSVG([wx,wy,ww,wh]){const id='wn'+(RID++);const r=rnd(77);const sh=wh*.52,lk=wh*.78;
   let s=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#070A1A"/><stop offset="1" stop-color="#22305A"/></linearGradient><linearGradient id="${id}k" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1A2648"/><stop offset="1" stop-color="#0A1022"/></linearGradient><radialGradient id="${id}m"><stop offset=".45" stop-color="#FFF6D0" stop-opacity=".5"/><stop offset="1" stop-color="#FFF6D0" stop-opacity="0"/></radialGradient>${blur(id+'b',6)}</defs><rect width="${ww}" height="${wh}" fill="url(#${id}s)"/>`;
   for(let k=0;k<30;k++)s+=`<circle cx="${f1(r()*ww)}" cy="${f1(r()*sh*.8)}" r="${f1(.6+r()*.9)}" fill="#fff" opacity="${f1(.3+r()*.5)}"/>`;
   const mx=ww*.68,my=wh*.2,mr=ww*.1;s+=`<circle cx="${f1(mx)}" cy="${f1(my)}" r="${f1(mr*2.6)}" fill="url(#${id}m)"/><circle cx="${f1(mx)}" cy="${f1(my)}" r="${f1(mr)}" fill="#FBF3D2"/><circle cx="${f1(mx-mr*.3)}" cy="${f1(my-mr*.2)}" r="${f1(mr*.22)}" fill="#D9CDA0" opacity=".6"/><circle cx="${f1(mx+mr*.3)}" cy="${f1(my+mr*.25)}" r="${f1(mr*.16)}" fill="#D9CDA0" opacity=".6"/>`;
   s+=`<g class="wbats">${[0,1].map(k=>`<g class="wbat" data-k="${k}"><path d="M0 0 C-3 -5 -8 -6 -14 -5 C-11 -2 -11 1 -12 3 C-9 2 -6 2 -4 5 C-3 2 -1 2 0 3 C1 2 3 2 4 5 C6 2 9 2 12 3 C11 1 11 -2 14 -5 C8 -6 3 -5 0 0Z" fill="#0A0A12"/></g>`).join('')}</g>`;
   let x=-10;while(x<ww+10){const h=wh*(.14+r()*.12);s+=pine(x,sh+4,h,'#0E1530');x+=10+r()*10}
   s+=`<rect y="${f1(sh)}" width="${ww}" height="${f1(lk-sh)}" fill="url(#${id}k)"/>`;
   for(let k=0;k<6;k++){const y=sh+8+k*((lk-sh-12)/6),w=ww*(.1+.03*k);s+=`<rect class="rip" data-k="${k}" x="${f1(mx-w/2)}" y="${f1(y)}" width="${f1(w)}" height="2.4" rx="1.2" fill="#FBF3D2" opacity="${f1(.6-k*.07)}"/>`}
   x=-20;while(x<ww+20){const h=wh*(.26+r()*.2);s+=pine(x,lk+6,h,'#060910');x+=18+r()*26}
   let g=`M0 ${f1(groundY(0,ww,wh)-8)}`;for(let xx=0;xx<=ww;xx+=10)g+=`L${xx} ${f1(groundY(xx,ww,wh)-8)}`;g+=`V${wh} H0Z`;
   s+=`<rect y="${f1(lk)}" width="${ww}" height="${f1(wh-lk)}" fill="#0B1410"/><path d="${g}" fill="#1E2A22"/><path d="M0 ${f1(wh-20)} H${ww}" stroke="#2E3E32" stroke-width="2" opacity=".7"/>`;
   for(let k=0;k<22;k++){const gx=r()*ww,gy=groundY(gx,ww,wh)-8;s+=`<path d="M${f1(gx)} ${f1(gy)} l-2 -6 M${f1(gx+2)} ${f1(gy)} l1 -7 M${f1(gx+4)} ${f1(gy)} l3 -5" stroke="#2E4234" stroke-width="1.4"/>`}
   s+=`<g opacity=".35" filter="url(#${id}b)">${[0,1].map(k=>`<ellipse class="fog" data-k="${k}" data-w="${ww}" cx="0" cy="${f1(lk-6+k*14)}" rx="${f1(ww*.45)}" ry="9" fill="#C9D2E8"/>`).join('')}</g>`;
   let ff='';for(let k=0;k<9;k++)ff+=`<circle class="ff" data-k="${k}" data-x="${f1(r()*ww)}" data-y="${f1(lk-30+r()*60)}" r="2.2" fill="#E9FF7A"/>`;
   return `<rect x="${wx-14}" y="${wy-14}" width="${ww+28}" height="${wh+28}" rx="6" fill="#221A2C"/><svg x="${wx}" y="${wy}" width="${ww}" height="${wh}" overflow="hidden">${s}${ff}<g class="walkers" data-w="${ww}" data-h="${wh}">${walkers()}</g></svg><rect x="${wx+ww/2-4}" y="${wy}" width="8" height="${wh}" fill="#221A2C"/><rect x="${wx}" y="${wy+wh/2-4}" width="${ww}" height="8" fill="#221A2C"/><rect x="${wx-22}" y="${wy+wh+12}" width="${ww+44}" height="10" rx="3" fill="#221A2C"/>`}

  /* ---------- walking past (feet on the ground at y=0, facing left) ---------- */
  const skin='#E8B48A',skin2='#B97E58';
  function leg(x,hipY,col,shoe,ph,w=6){return `<g class="lg" data-x="${x}" data-p="${hipY}" data-ph="${ph}"><path d="M${x} ${hipY} L${x} -3" stroke="${col}" stroke-width="${w}" stroke-linecap="round"/><path d="M${x+2} -1 h-8 a3 3 0 0 1 0 -5 h6Z" fill="${shoe}" stroke="${shoe}" stroke-width="2" stroke-linejoin="round"/></g>`}
  function face(x,y,r,hair,sk=skin){return `<circle cx="${x}" cy="${y}" r="${r}" fill="${sk}"/><circle cx="${x-r*.45}" cy="${y-r*.1}" r="${r*.13}" fill="#2A1A10"/><path d="M${x-r*.6} ${y+r*.35} q${r*.25} ${r*.2} ${r*.5} 0" stroke="#7A3A20" stroke-width="1.3" fill="none" stroke-linecap="round"/><circle cx="${x-r*.15}" cy="${y+r*.25}" r="${r*.2}" fill="#E07A6A" opacity=".45"/>`+(hair?`<path d="M${x-r} ${y-r*.1} C${x-r} ${y-r*1.3} ${x+r*1.1} ${y-r*1.4} ${x+r} ${y+r*.2} C${x+r*.6} ${y-r*.3} ${x} ${y-r*.6} ${x-r} ${y-r*.1}Z" fill="${hair}"/>`:'')}
  function person(x,o){const H=o.h||1;let s=(o.back||'')+leg(x-5,-44*H,o.legs,'#15121A',0)+leg(x+5,-44*H,o.legs,'#15121A',1);
   s+=`<path d="M${x-14} ${-90*H} h28 l5 ${48*H} h-38Z" fill="${o.coat}"/><path d="M${x} ${-90*H} v${46*H}" stroke="#000" stroke-opacity=".18" stroke-width="2"/>`;
   if(o.scarf)s+=`<rect x="${x-12}" y="${-92*H}" width="24" height="7" rx="3.5" fill="${o.scarf}"/>`;
   s+=face(x,-102*H,11,o.hair,o.skin||skin)+(o.hat||'');return s}
  function catLeg(x,hip,ph){return `<g class="lg" data-x="${x}" data-p="${hip}" data-ph="${ph}" data-amp="28"><path d="M${x} ${hip} L${x} -2" stroke="#121118" stroke-width="4.6" stroke-linecap="round"/><ellipse cx="${x-1}" cy="-1.4" rx="3.6" ry="2" fill="#121118"/></g>`}
  function wCat(){/* a black cat trotting: diagonal legs move together, tail up with a curl */
   let s=catLeg(-14,-17,1)+catLeg(16,-17,0);
   s+=`<g class="ctail"><path d="M22 -24 C32 -30 30 -46 36 -50 C40 -53 42 -48 39 -46" stroke="#121118" stroke-width="5" fill="none" stroke-linecap="round"/></g>`;
   s+=`<ellipse cx="2" cy="-23" rx="22" ry="9.5" fill="#121118"/><ellipse cx="-14" cy="-24" rx="9" ry="10" fill="#121118"/>`;
   s+=`<circle cx="-24" cy="-33" r="8.5" fill="#121118"/><path d="M-30 -38 L-30 -48 L-23 -41Z M-20 -40 L-17 -49 L-14 -39Z" fill="#121118"/><ellipse cx="-28" cy="-34" rx="2.2" ry="2.6" fill="#C9EF4E"/><ellipse cx="-28.4" cy="-34" rx=".8" ry="2.1" fill="#000"/><path d="M-33 -30 l-6 -1 M-33 -29 l-6 2" stroke="#8C8899" stroke-width=".7"/>`;
   s+=catLeg(-10,-17,0)+catLeg(20,-17,1);
   return `<g class="wk" data-off="0" data-s="1.05" data-v="1.25">${s}</g>`}
  function wFlash(){/* a kid in a yellow raincoat with the hood up, shining a torch at the path */
   let s=leg(-4,-30,'#2E3A5A','#C0202E',0)+leg(4,-30,'#2E3A5A','#C0202E',1);
   s+=`<path class="beam" d="M-24 -58 L-110 -4 L-58 2Z" fill="#FFF3B0" opacity=".22"/>`;
   s+=`<path d="M-12 -64 h24 l3 36 h-30Z" fill="#E8C23A"/><path d="M0 -62 v30" stroke="#B8921A" stroke-width="1.5"/><circle cx="0" cy="-54" r="1.5" fill="#B8921A"/><circle cx="0" cy="-44" r="1.5" fill="#B8921A"/>`;
   s+=`<path d="M-4 -58 L-16 -58" stroke="#D8B230" stroke-width="7" stroke-linecap="round"/><circle cx="-17" cy="-58" r="3.6" fill="${skin2}"/><rect x="-28" y="-61" width="11" height="6" rx="1.5" fill="#3A3E46"/><rect x="-30" y="-62" width="3" height="8" rx="1" fill="#FFF3B0"/>`;
   s+=`<path d="M-12 -72 C-12 -90 12 -92 13 -72 L12 -64 H-11Z" fill="#E8C23A"/>`+face(-1,-73,9,'#2A1A10',skin2);
   return `<g class="wk" data-off="8" data-s=".95">${s}</g>`}
  function wBrolly(){/* someone strolling under a big black umbrella */
   let s=person(0,{legs:'#2A2A3A',coat:'#3A5A6A',scarf:'#C0202E',hair:'#7A4A22'});
   s+=`<path d="M-6 -86 L-12 -112" stroke="#2E4A58" stroke-width="7" stroke-linecap="round"/><circle cx="-12" cy="-114" r="3.6" fill="${skin}"/>`;
   s+=`<path d="M-12 -110 V-160" stroke="#1A1A22" stroke-width="2.4"/><path d="M-12 -110 q0 6 5 6" stroke="#1A1A22" stroke-width="2.4" fill="none"/>`;
   s+=`<path d="M-58 -152 Q-58 -192 -12 -194 Q34 -192 34 -152 Q23 -160 11 -152 Q0 -160 -12 -152 Q-24 -160 -35 -152 Q-46 -160 -58 -152Z" fill="#1A1A22"/><path d="M-12 -194 Q-24 -176 -35 -152 M-12 -194 Q0 -176 11 -152" stroke="#33333E" stroke-width="1.5" fill="none"/><circle cx="-12" cy="-196" r="2.6" fill="#1A1A22"/>`;
   return `<g class="wk" data-off="15" data-s=".9">${s}</g>`}
  function wCamper(){/* a camper with a backpack, carrying a glowing lantern */
   let s=person(0,{legs:'#3A3428',coat:'#5A6A3A',scarf:'#E8A23A',hair:'#2A1A10',skin:skin2,back:`<rect x="6" y="-92" width="18" height="40" rx="6" fill="#8A3A2A"/><rect x="8" y="-60" width="14" height="6" rx="2" fill="#6A2A1A"/>`,hat:`<path d="M-11 -106 C-11 -122 11 -122 11 -106Z" fill="#C0202E"/><circle cx="0" cy="-121" r="3.4" fill="#E8E2D4"/>`});
   s+=`<path d="M-6 -84 L-18 -60" stroke="#4A5A2E" stroke-width="7" stroke-linecap="round"/><circle cx="-19" cy="-58" r="3.6" fill="${skin2}"/>`;
   s+=`<g class="lant"><path d="M-19 -56 v5" stroke="#2A2620" stroke-width="1.4"/><circle class="lglow" cx="-19" cy="-40" r="24" fill="#FFD58A" opacity=".25"/><rect x="-25" y="-51" width="12" height="3" rx="1" fill="#2A2620"/><rect x="-24" y="-48" width="10" height="14" rx="2" fill="#FFE6A0" stroke="#2A2620" stroke-width="1.4"/><rect x="-25" y="-34" width="12" height="3" rx="1" fill="#2A2620"/></g>`;
   return `<g class="wk" data-off="22" data-s=".92">${s}</g>`}
  function walkers(){return wCat()+wFlash()+wBrolly()+wCamper()}

  /* ---------- the lucky-and-unlucky corner (floor at y=0, x from -230 to 290) ---------- */
  function corner(){const p='cn'+(RID++);let s=`<defs>
   <linearGradient id="${p}w" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#3A2416"/><stop offset=".45" stop-color="#7A5034"/><stop offset="1" stop-color="#2A180E"/></linearGradient>
   <linearGradient id="${p}c" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4A2E5E"/><stop offset="1" stop-color="#2A1838"/></linearGradient>
   <linearGradient id="${p}b" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFF0C0"/><stop offset=".45" stop-color="#C9963A"/><stop offset="1" stop-color="#6A4A12"/></linearGradient>
   <radialGradient id="${p}o" cx=".4" cy=".35" r=".7"><stop offset="0" stop-color="#E8F4FF" stop-opacity=".95"/><stop offset=".5" stop-color="#7A90C8" stop-opacity=".55"/><stop offset="1" stop-color="#2A2A5A" stop-opacity=".9"/></radialGradient>
   <radialGradient id="${p}g"><stop offset="0" stop-color="#FFD58A" stop-opacity=".55"/><stop offset=".4" stop-color="#FFB050" stop-opacity=".18"/><stop offset="1" stop-color="#FFB050" stop-opacity="0"/></radialGradient>
   <radialGradient id="${p}x"><stop offset="0" stop-color="#B9F08A" stop-opacity=".45"/><stop offset="1" stop-color="#B9F08A" stop-opacity="0"/></radialGradient>
   <clipPath id="${p}k"><circle cx="-80" cy="-346" r="54"/></clipPath>${blur(p+'bl',3)}</defs>`;
   /* the candle's warm light on the wall */
   s+=`<circle class="cglow" cx="44" cy="-420" r="260" fill="url(#${p}g)"/>`;
   /* the wall calendar on a nail, open on Friday the 13th */
   s+=`<g><circle cx="-10" cy="-668" r="6" fill="#8A8F98"/><path d="M-10 -666 L-44 -640 M-10 -666 L24 -640" stroke="#2A2620" stroke-width="2"/>
   <rect x="-82" y="-642" width="144" height="190" rx="4" fill="#000" opacity=".35" transform="translate(5 6)"/><rect x="-82" y="-642" width="144" height="190" rx="4" fill="#F6F1E6"/>
   <rect x="-82" y="-642" width="144" height="40" rx="4" fill="#C0202E"/><rect x="-82" y="-608" width="144" height="6" fill="#C0202E"/>${[-62,-38,-14,10,34,52].map(x=>`<circle cx="${x}" cy="-646" r="3.5" fill="#2A2620"/>`).join('')}
   ${T(-10,-614,22,'FRIDAY',{w:800,anchor:'middle',fill:'#fff'})}${T(-10,-500,96,'13',{font:'Georgia,serif',w:700,anchor:'middle',fill:'#16151B'})}${T(-10,-468,16,'Lucky you',{w:600,anchor:'middle',fill:'#8A7F6A'})}
   <path class="page" d="M62 -452 L62 -480 Q50 -462 34 -452Z" fill="#E6DECD"/></g>`;
   /* the horseshoe, hung the lucky way up, on two nails */
   s+=`<g transform="translate(-196 -560)"><path d="M-30 -10 V30 A30 30 0 0 0 30 30 V-10" fill="none" stroke="#2A2D33" stroke-width="18"/><path d="M-30 -10 V30 A30 30 0 0 0 30 30 V-10" fill="none" stroke="url(#${p}b)" stroke-width="14"/><path d="M-30 -10 V30 A30 30 0 0 0 30 30 V-10" fill="none" stroke="#6A4A12" stroke-width="3" opacity=".6"/>${[[-30,0],[-30,22],[30,0],[30,22],[-14,56],[14,56]].map(([x,y])=>`<rect x="${x-2}" y="${y-3}" width="4" height="6" rx="1" fill="#3A2A08"/>`).join('')}<circle cx="-30" cy="-6" r="4" fill="#8A8F98"/><circle cx="30" cy="-6" r="4" fill="#8A8F98"/></g>`;
   /* small round table with a velvet cloth and gold fringe */
   s+=`<ellipse cx="0" cy="-4" rx="120" ry="12" fill="#000" opacity=".5"/>
   <path d="M-14 -250 L-10 -40 L-70 -6 L-60 0 L0 -26 L60 0 L70 -6 L10 -40 L14 -250Z" fill="url(#${p}w)" stroke="#1A0E06" stroke-width="1.5"/>
   <path d="M-160 -268 Q-166 -200 -150 -168 Q-80 -150 0 -150 Q80 -150 150 -168 Q166 -200 160 -268Z" fill="url(#${p}c)"/>
   <path d="M-150 -168 Q-80 -150 0 -150 Q80 -150 150 -168" stroke="#C9963A" stroke-width="5" fill="none" stroke-dasharray="2 4"/>${[-120,-60,0,60,120].map(x=>`<path d="M${x} ${-200+Math.abs(x)*.12} q4 30 0 ${46-Math.abs(x)*.12}" stroke="#000" stroke-opacity=".22" stroke-width="10" fill="none"/>`).join('')}
   <ellipse cx="0" cy="-268" rx="160" ry="22" fill="#5A3A70"/><ellipse cx="0" cy="-270" rx="160" ry="22" fill="none" stroke="#C9963A" stroke-width="2.5"/>`;
   /* the crystal ball on a brass stand, mist swirling inside */
   s+=`<circle class="xglow" cx="-80" cy="-346" r="100" fill="url(#${p}x)"/><path d="M-118 -270 Q-80 -262 -42 -270 L-52 -292 Q-80 -298 -108 -292Z" fill="url(#${p}b)" stroke="#5A3E10"/><ellipse cx="-80" cy="-292" rx="28" ry="6" fill="#6A4A12"/>
   <circle cx="-80" cy="-346" r="54" fill="url(#${p}o)"/><g clip-path="url(#${p}k)"><g class="swirl" data-x="-80" data-y="-346"><path d="M-120 -350 C-100 -380 -60 -330 -40 -360 M-118 -330 C-96 -300 -70 -350 -44 -326" stroke="#C9EFA0" stroke-width="7" fill="none" opacity=".55" stroke-linecap="round" filter="url(#${p}bl)"/><path d="M-110 -370 C-90 -340 -70 -390 -50 -350" stroke="#E8D8FF" stroke-width="5" fill="none" opacity=".5" stroke-linecap="round" filter="url(#${p}bl)"/></g></g>
   <circle cx="-80" cy="-346" r="54" fill="none" stroke="#fff" stroke-opacity=".35" stroke-width="2"/><ellipse cx="-98" cy="-370" rx="14" ry="9" fill="#fff" opacity=".7" transform="rotate(-30 -98 -370)"/>`;
   /* the candle in a brass holder, wax drips, a real flickering flame */
   s+=`<g transform="translate(-26 0)"><ellipse cx="70" cy="-274" rx="40" ry="9" fill="url(#${p}b)" stroke="#5A3E10"/><path d="M106 -276 q20 -4 18 10 q-2 10 -14 6" stroke="url(#${p}b)" stroke-width="5" fill="none"/>
   <rect x="56" y="-392" width="28" height="118" rx="3" fill="#EFE6D2"/><rect x="56" y="-392" width="9" height="118" fill="#fff" opacity=".4"/><path d="M56 -390 q0 18 4 20 q4 -2 4 -12 q2 26 6 26 q4 0 4 -20 q2 8 6 8 q4 -2 4 -12 V-392Z" fill="#F8F2E4"/><ellipse cx="70" cy="-392" rx="14" ry="4" fill="#E2D6BC"/>
   <path d="M70 -392 v-10" stroke="#2A2620" stroke-width="2"/><path class="flame" data-x="70" data-y="-400" d="" fill="#FFC85A"/><path class="flame2" data-x="70" data-y="-400" d="" fill="#FFF3C8"/></g>`;
   /* the tipped-over salt shaker with a little spill */
   s+=`<g transform="translate(112 -284) rotate(78)"><rect x="-12" y="-30" width="24" height="40" rx="5" fill="#E8EEF4" opacity=".9" stroke="#9AA6B6"/><rect x="-12" y="-6" width="24" height="16" rx="3" fill="#fff"/><path d="M-12 -30 h24 v-6 q-12 -8 -24 0Z" fill="#B8C0CC" stroke="#7A8494"/><circle cx="-5" cy="-34" r="1.2" fill="#555"/><circle cx="0" cy="-35" r="1.2" fill="#555"/><circle cx="5" cy="-34" r="1.2" fill="#555"/></g>
   <path d="M140 -268 q10 -8 20 0Z" fill="#fff" opacity=".9"/>${Array.from({length:6},(_,k)=>`<circle cx="${140+k*3.2}" cy="${-266+((k*7)%5)-3}" r="1.4" fill="#fff"/>`).join('')}`;
   /* the sleeping black cat curled up on a round cushion */
   s+=`<g class="ccat" transform="translate(270 0) scale(1.45)"><ellipse cx="0" cy="-4" rx="78" ry="10" fill="#000" opacity=".45"/><ellipse cx="0" cy="-20" rx="76" ry="22" fill="#7A2A3A"/><ellipse cx="0" cy="-28" rx="70" ry="17" fill="#9A3A4A"/>${[-48,-24,0,24,48].map(x=>`<circle cx="${x}" cy="-28" r="2.4" fill="#5A1A28"/>`).join('')}
   <g class="breathe"><path d="M-40 -32 C-62 -40 -58 -86 -10 -92 C36 -96 66 -70 62 -46 C60 -32 48 -28 30 -28 Z" fill="#16151B"/><path d="M-30 -80 C-6 -92 34 -88 54 -64" stroke="#3C3A4C" stroke-width="2.4" fill="none" stroke-linecap="round"/>
   <path d="M20 -58 C30 -62 40 -58 44 -50" stroke="#24232C" stroke-width="2" fill="none"/></g>
   <g class="ctail2"><path d="M58 -40 C62 -22 30 -18 -4 -22 C-24 -24 -40 -26 -50 -32" stroke="#16151B" stroke-width="13" fill="none" stroke-linecap="round"/><path d="M50 -28 C30 -22 0 -24 -30 -26" stroke="#3C3A4C" stroke-width="1.6" fill="none" opacity=".7"/></g>
   <g class="breathe"><g class="chead"><path d="M-62 -38 C-66 -58 -52 -70 -38 -70 C-22 -70 -12 -58 -14 -44 C-16 -34 -26 -30 -38 -30 C-50 -30 -60 -32 -62 -38Z" fill="#16151B"/><g class="ear"><path d="M-58 -60 L-64 -84 L-46 -68Z" fill="#16151B"/><path d="M-58 -64 L-61 -78 L-51 -68Z" fill="#E59AAE" opacity=".7"/></g><path d="M-34 -68 L-22 -88 L-20 -62Z" fill="#16151B"/><path d="M-31 -68 L-23 -82 L-22 -66Z" fill="#E59AAE" opacity=".7"/>
   <path class="lid" data-k="0" d="M-55 -50 q5 4 10 0" stroke="#9A96AA" stroke-width="2.2" fill="none" stroke-linecap="round"/><g class="eye" data-k="0" data-y="-50" transform="translate(0 -50) scale(1 0) translate(0 50)"><ellipse cx="-50" cy="-50" rx="4.6" ry="4.8" fill="#C9EF4E"/><ellipse cx="-50" cy="-50" rx="1.4" ry="4" fill="#0A0A0A"/><circle cx="-51.4" cy="-51.8" r="1.2" fill="#fff"/></g><path class="lid" data-k="1" d="M-35 -50 q5 4 10 0" stroke="#9A96AA" stroke-width="2.2" fill="none" stroke-linecap="round"/><g class="eye" data-k="1" data-y="-50" transform="translate(0 -50) scale(1 0) translate(0 50)"><ellipse cx="-30" cy="-50" rx="4.6" ry="4.8" fill="#C9EF4E"/><ellipse cx="-30" cy="-50" rx="1.4" ry="4" fill="#0A0A0A"/><circle cx="-31.4" cy="-51.8" r="1.2" fill="#fff"/></g><path d="M-43 -43 l3 3 l3 -3Z" fill="#E59AAE"/><path d="M-60 -44 l-12 -2 M-60 -41 l-12 2 M-22 -44 l12 -2 M-22 -41 l12 2" stroke="#8C8899" stroke-width="1"/><g class="yawn" transform="translate(0 -38) scale(1 0) translate(0 38)"><ellipse cx="-40" cy="-35" rx="6" ry="5.5" fill="#4A1420"/><ellipse cx="-40" cy="-32.5" rx="3.6" ry="2.4" fill="#E07A8E"/><path d="M-44.5 -38.5 l1.2 2.4 l1.2 -2.4 M-37.5 -38.5 l1.2 2.4 l1.2 -2.4" fill="#fff"/></g></g>
   <ellipse cx="-30" cy="-30" rx="11" ry="5" fill="#16151B"/></g>
   <g class="zz">${T(-30,-104,20,'z',{w:800,fill:'#C9C2E6',anchor:'middle'})}${T(-16,-122,26,'z',{w:800,fill:'#C9C2E6',anchor:'middle'})}</g></g>`;
   return `<g class="cscene">${s}</g>`}


  // ---------- the room, wide (TV, web) and tall (phone) ----------
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1590, 140, 280, 350], corner: [330, 868, 0.86] },
    tall: { w: 390, h: 844, win: null, corner: [150, 834, 0.44] },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t), wake(t) }: at poses everything at t seconds, and
   * wake(t) makes the cat wake up and yawn starting at t.
   * opts: { layout: "wide"|"tall", align: "xMidYMax slice" }
   */
  function build(svg, opts) {
    const L = LAYOUTS[(opts && opts.layout) || "wide"];
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", (opts && opts.align) || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win);
    svg.innerHTML = rm.bg + `<g transform="translate(${L.corner[0]} ${L.corner[1]}) scale(${L.corner[2]})">${corner()}</g>` + rm.vig;
    return pose(svg);
  }

  const ease = (x) => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x));
  const ramp = (w, a, b) => ease((w - a) / (b - a));

  /* gathers every moving part, and returns at(t) and wake(t) */
  function pose(root) {
    const q = (s) => [...root.querySelectorAll(s)];
    const bulbs = q(".bulb"), cglow = q(".cglow"), xglow = q(".xglow");
    const flames = q(".flame").map((f) => ({ f, f2: f.nextElementSibling, x: +f.dataset.x, y: +f.dataset.y }));
    const swirls = q(".swirl"), br = q(".breathe"), zz = q(".zz"), ears = q(".ear"), tails2 = q(".ctail2"), pages = q(".page");
    const heads = q(".chead"), lids = q(".lid"), eyes = q(".eye"), yawns = q(".yawn");
    const ff = q(".ff").map((c) => ({ c, k: +c.dataset.k, x: +c.dataset.x, y: +c.dataset.y }));
    const fog = q(".fog").map((c) => ({ c, k: +c.dataset.k, w: +c.dataset.w }));
    const rips = q(".rip"), bats = q(".wbat").map((b) => ({ b, k: +b.dataset.k, W: +b.closest("svg").getAttribute("width") }));
    const walkers = q(".walkers").map((W) => ({ ww: +W.dataset.w, wh: +W.dataset.h, wk: [...W.querySelectorAll(".wk")] }));
    let wakeAt = null;
    function at(t) {
      // every 15 s the lights flicker twice, like a storm nearby
      const fl = t % 15;
      const dip = (fl > 11 && fl < 11.12) || (fl > 11.3 && fl < 11.38) || (fl > 11.5 && fl < 11.56) ? 0.25 : 1;
      bulbs.forEach((b) => { const k = +b.dataset.k; b.setAttribute("opacity", (dip * (0.8 + 0.2 * Math.sin(t * wq(1.6) + k * 1.9) * Math.sin(t * wq(0.7) + k))).toFixed(3)); });
      cglow.forEach((g) => g.setAttribute("opacity", (0.85 + 0.15 * Math.sin(t * wq(7)) * Math.sin(t * wq(3.1))).toFixed(3)));
      xglow.forEach((g) => g.setAttribute("opacity", (0.6 + 0.4 * Math.sin(t * wq(1.2))).toFixed(3)));
      flames.forEach((F) => { F.f.setAttribute("d", flameD(F.x, F.y, 16, 40, t, 0)); F.f2.setAttribute("d", flameD(F.x, F.y, 8, 20, t, 1.3)); });
      swirls.forEach((g) => g.setAttribute("transform", `rotate(${f1((t * 24) % 360)} ${g.dataset.x} ${g.dataset.y})`));
      // the cat: asleep, or waking up (w goes 0 to 1 over WAKE seconds after a profile is picked)
      const w = wakeAt == null ? 0 : Math.max(0, Math.min(1, (t - wakeAt) / WAKE));
      br.forEach((g) => { const s = 1 + 0.03 * Math.sin(t * wq(1.6)) * (1 - w); g.setAttribute("transform", `translate(0 ${f1(30 * (s - 1))}) scale(1 ${s.toFixed(4)})`); });
      zz.forEach((g) => { const a = (t * 10 / LOOP) % 1; g.setAttribute("transform", `translate(${f1(a * 14)} ${f1(-a * 30)})`); g.setAttribute("opacity", ((a < 0.15 ? a / 0.15 : a > 0.8 ? (1 - a) / 0.2 : 1) * (1 - ramp(w, 0, 0.2))).toFixed(2)); });
      const twitch = (t % 7.5) < 0.25 ? -10 : 0;
      ears.forEach((g) => g.setAttribute("transform", `rotate(${f1(w > 0 ? -12 * ramp(w, 0.1, 0.3) : twitch)} -54 -66)`));
      tails2.forEach((g) => g.setAttribute("transform", `rotate(${f1(Math.max(0, Math.sin(t * wq(1.3))) * 3 + 6 * Math.sin(w * Math.PI))} 58 -40)`));
      // the head lifts, one eye opens, then the other, then a big yawn
      const lift = ramp(w, 0.05, 0.35);
      heads.forEach((g) => g.setAttribute("transform", `translate(${f1(-2 * lift)} ${f1(-6 * lift)}) rotate(${f1(-10 * lift + 4 * Math.sin(Math.PI * ramp(w, 0.4, 0.95)))} -38 -34)`));
      const open = [ramp(w, 0.08, 0.2), ramp(w, 0.28, 0.4)];
      const yawn = Math.sin(Math.PI * ramp(w, 0.42, 0.95));
      eyes.forEach((g) => { const k = +g.dataset.k; const o = open[k] * (1 - 0.75 * yawn); g.setAttribute("transform", `translate(0 -50) scale(1 ${o.toFixed(3)}) translate(0 50)`); });
      lids.forEach((p) => p.setAttribute("opacity", open[+p.dataset.k] > 0.15 ? "0" : "1"));
      yawns.forEach((g) => g.setAttribute("transform", `translate(0 -38) scale(1 ${yawn.toFixed(3)}) translate(0 38)`));
      pages.forEach((p) => { const a = Math.max(0, Math.sin(t * wq(0.9))); p.setAttribute("d", `M62 -452 L62 ${f1(-480 - a * 8)} Q${f1(50 - a * 6)} ${f1(-462 - a * 4)} ${f1(34 - a * 8)} -452Z`); });
      ff.forEach((F) => { F.c.setAttribute("cx", f1(F.x + Math.sin(t * wq(0.6) + F.k) * 12)); F.c.setAttribute("cy", f1(F.y + Math.sin(t * wq(0.9) + F.k * 2) * 8)); F.c.setAttribute("opacity", Math.max(0, Math.sin(t * wq(1.4) + F.k * 1.7)).toFixed(2)); });
      // mist drifts back and forth over the lake
      fog.forEach((F) => F.c.setAttribute("cx", f1(F.w * 0.5 + Math.sin(t * TAU / LOOP + F.k * Math.PI) * F.w * 0.3)));
      rips.forEach((r) => { const k = +r.dataset.k; r.setAttribute("transform", `translate(${f1(Math.sin(t * wq(1.5) + k) * 4)} 0)`); });
      bats.forEach(({ b, k, W }) => { const P = 15, a = ((t + k * 5) % P) / P; const x = -20 + a * (W + 40), y = 40 + k * 26 + Math.sin(a * 12) * 10; const fl2 = Math.abs(Math.sin(t * wq(14) + k)) * 0.7 + 0.3; b.setAttribute("transform", `translate(${f1(x)} ${f1(y)}) scale(${k ? 0.9 : 1.1} ${fl2.toFixed(2)})`); });
      walkers.forEach(({ ww, wh, wk }) => {
        const v = (ww + 300) / 9;
        wk.forEach((g) => {
          const off = +g.dataset.off, sc = 1.5 * (+g.dataset.s), vm = +(g.dataset.v || 1);
          const tt = (t + LOOP - off) % LOOP; const x = ww + 150 - tt * v * vm;
          const y = groundY(x, ww, wh) - 6; const cad = vm > 1 ? 10 : 7; const bob = Math.abs(Math.sin(tt * cad)) * 1.5;
          g.setAttribute("transform", `translate(${f1(x)} ${f1(y - bob)}) scale(${sc})`);
          g.querySelectorAll(".lg").forEach((l) => { const amp = +(l.dataset.amp || 22); const sw = Math.sin(tt * cad + (+l.dataset.ph) * Math.PI) * amp; l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} ${l.dataset.p})`); });
          const ct = g.querySelector(".ctail"); if (ct) ct.setAttribute("transform", `rotate(${f1(Math.sin(tt * 3) * 8)} 22 -24)`);
          const bm = g.querySelector(".beam"); if (bm) bm.setAttribute("opacity", (0.18 + 0.06 * Math.sin(tt * 9)).toFixed(2));
          const lt2 = g.querySelector(".lant"); if (lt2) lt2.setAttribute("transform", `rotate(${f1(Math.sin(tt * cad) * 6)} -19 -56)`);
        });
      });
    }
    return { at, wake: (t) => { wakeAt = t; } };
  }

  /** Plays a built room live until the element leaves the page. Returns the clock, so wake() can start now. */
  function play(svg, scene) {
    const start = performance.now() - 4000;
    const now = () => (performance.now() - start) / 1000;
    const tick = () => { if (!svg.isConnected) return; scene.at(now()); requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
    return now;
  }

  G.BenflixF13 = { LOOP, WAKE, season, greeting, line, build, play, LAYOUTS };
})(typeof window !== "undefined" ? window : globalThis);
