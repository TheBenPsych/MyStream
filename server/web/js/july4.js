/* Benflix 4th of July: the Who's watching room (approved 2026-11-17, thread "Design a 4th of July theme for Benflix").
 *
 * A dim room on a summer night, with paper pennant flags strung along the top wall (clear of the logo). Bottom
 * left is a little cookout table with a red and white checked cloth: an old teal desk fan turning side to side
 * with its blades spinning, two small flags in a glass jar that flutter when the fan faces them, a flag cake on
 * a stand, a pitcher of lemonade with lemon slices, ice and rising bubbles, a glass with a striped straw, and
 * watermelon slices on a plate. Through the window: a street of houses with porch flags, fireflies, the odd red,
 * white or blue firework over the roofs (the room glows with its colour), and people walking past: a dog in a
 * bandana, a kid waving a flag, a marching drummer kid, someone carrying a watermelon, and a couple with a picnic
 * basket and a lawn chair. 1 to 4 July. On the 4th from 9 PM the "night" show fills the window with fireworks.
 *
 * Like feast.js, the same drawing is used live on the web and by tools/july4/record.js, which records it as
 * looping videos for the TV and phone app. Everything is a function of the time t (seconds) and repeats every
 * LOOP seconds, so a video of one loop joins up with no jump.
 * The drawing code comes straight from the approved design sheet (july4-2-whos-watching.html).
 */
(function (G) {
  "use strict";
  const LOOP = 30;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;

  // ---------- dates (the same as JulySeason in the app) ----------
  /** 1 to 4 July */
  const season = (d) => d.getMonth() === 6 && d.getDate() <= 4;
  /** "night" on the 4th from 9 PM (the big fireworks show), else "day" */
  const variant = (d) => (d.getMonth() === 6 && d.getDate() === 4 && d.getHours() >= 21 ? "night" : "day");
  const greeting = () => "Happy 4th of July";
  /** The line under the greeting: "3 days to the Fourth", "Fireworks tonight at 9", then "The fireworks are on!" */
  const countdown = (d) => {
    if (!season(d)) return "";
    const days = 4 - d.getDate();
    if (days === 0) return d.getHours() >= 21 ? "The fireworks are on!" : "Fireworks tonight at 9";
    return days === 1 ? "The Fourth is tomorrow" : `${days} days to the Fourth`;
  };

  // ---------- drawing kit (from the approved design sheet) ----------
  let UID=0;const u=p=>p+(UID++)+'_';
  const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
  const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
  const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
  const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
  const f1=v=>(+v).toFixed(1);
  const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;

  const NAVY='#24366E',FRED='#D63445',CREAM='#F4F0E8';
  function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
  let RID=0;
  function groundY(x,ww,wh){return wh-34+Math.sin(x*.02)*2}
  const sh=(id,s)=>`<filter id="${id}" x="-40%" y="-40%" width="180%" height="180%"><feDropShadow dx="0" dy="${f1(s*.6)}" stdDeviation="${s}" flood-color="#000" flood-opacity=".5"/></filter>`;
  function starPath(x,y,R,ri=.42){let d='';for(let k=0;k<10;k++){const a=-Math.PI/2+k*Math.PI/5,rr=k%2?R*ri:R;d+=(k?'L':'M')+f1(x+Math.cos(a)*rr)+' '+f1(y+Math.sin(a)*rr)}return d+'Z'}
  const wstar=(x,y,R,c='#fff')=>`<path d="${starPath(x,y,R)}" fill="${c}"/>`;

  /* ---------- the room: deep navy summer-night walls, a string of paper pennant flags ---------- */
  function room(W2,H2,win,flags=true){const id='rm'+(RID++);const fl=H2*.8;
   let s=`<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0A0E1E"/><stop offset="1" stop-color="#151B36"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2A1D14"/><stop offset="1" stop-color="#0C0907"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".6"/></radialGradient><radialGradient id="${id}lamp" cx=".18" cy=".72" r=".5"><stop offset="0" stop-color="#FFC878" stop-opacity=".22"/><stop offset="1" stop-color="#FFC878" stop-opacity="0"/></radialGradient></defs>`;
   s+=`<rect width="${W2}" height="${H2}" fill="url(#${id}w)"/>`;for(let x=0;x<W2;x+=W2/24)s+=`<rect x="${f1(x)}" y="0" width="${f1(W2/48)}" height="${fl}" fill="#fff" opacity=".014"/>`;
   s+=`<rect y="${f1(fl-H2*.16)}" width="${W2}" height="${f1(H2*.16)}" fill="#000" opacity=".2"/><rect y="${f1(fl-H2*.16)}" width="${W2}" height="4" fill="#2A3352" opacity=".8"/>`;
   if(win)s+=windowSVG(win);
   s+=`<rect y="${fl}" width="${W2}" height="${H2-fl}" fill="url(#${id}f)"/>`;for(let k=1;k<6;k++)s+=`<path d="M0 ${f1(fl+(H2-fl)*k/6)} H${W2}" stroke="#000" stroke-opacity=".25" stroke-width="2"/>`;
   s+=`<rect y="${fl-10}" width="${W2}" height="10" fill="#0C0F1C"/><rect width="${W2}" height="${H2}" fill="url(#${id}lamp)"/>`;
   if(flags){/* paper pennant flags on a cord, two swags, kept clear of the logo */const sc=W2/1920;const swags=W2<600?[[0,18,W2,18,30]]:[[330*sc,30*sc,820*sc,30*sc,90*sc],[820*sc,30*sc,1310*sc,30*sc,90*sc]];
    const PC=[FRED,CREAM,'#2E4FA8'];swags.forEach(([x1,y1,x2,y2,sag],si)=>{const N=Math.round((x2-x1)/(W2<600?30:44*sc));const pt=t=>[x1+(x2-x1)*t,y1+(y2-y1)*t+sag*4*t*(1-t)];
     let wire='';for(let k=0;k<=40;k++){const [x,y]=pt(k/40);wire+=(k?'L':'M')+f1(x)+' '+f1(y)}s+=`<path d="${wire}" stroke="#C9B48A" stroke-width="${f1(2.4*Math.max(sc,.5))}" fill="none"/>`;
     for(let k=0;k<N;k++){const t0=(k+.12)/N,t1=(k+.88)/N;const [ax,ay]=pt(t0),[bx,by]=pt(t1);const mx=(ax+bx)/2,my=(ay+by)/2,h=(W2<600?26:44*sc);const c=PC[(k+si)%3];
      s+=`<g class="pen" data-x="${f1(mx)}" data-y="${f1(my)}" data-k="${k+si*20}"><path d="M${f1(ax)} ${f1(ay)}L${f1(bx)} ${f1(by)}L${f1(mx)} ${f1(my+h)}Z" fill="${c}"/><path d="M${f1(mx)} ${f1(my)}L${f1(bx)} ${f1(by)}L${f1(mx)} ${f1(my+h)}Z" fill="#000" opacity=".16"/>${c===CREAM?'':wstar(mx,my+h*.36,h*.17)}</g>`}})}
   return {bg:s,vig:`<rect width="${W2}" height="${H2}" fill="url(#${id}v)"/><rect class="flash" width="${W2}" height="${H2}" fill="#FFFFFF" opacity="0"/>`}}

  /* ---------- the window: a summer street at night, houses with porch flags, fireflies, fireworks over the roofs ---------- */
  function windowSVG([wx,wy,ww,wh]){const id='wn'+(RID++);const r=rnd(44);
   let s=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#070B22"/><stop offset=".6" stop-color="#1C2452"/><stop offset="1" stop-color="#4A3A62"/></linearGradient><radialGradient id="${id}l"><stop offset="0" stop-color="#FFD98A" stop-opacity=".6"/><stop offset="1" stop-color="#FFD98A" stop-opacity="0"/></radialGradient></defs><rect width="${ww}" height="${wh}" fill="url(#${id}s)"/>`;
   for(let k=0;k<22;k++)s+=`<circle cx="${f1(r()*ww)}" cy="${f1(r()*wh*.35)}" r="${f1(.6+r()*.9)}" fill="#fff" opacity="${f1(.3+r()*.5)}"/>`;
   const FC=[['#FF5A6A','#FFFFFF'],['#8EAEFF','#FFFFFF'],['#FF5A6A','#8EAEFF'],['#FFFFFF','#FFE08A'],['#8EAEFF','#FF5A6A']];
   for(let k=0;k<5;k++){const cx=ww*(.16+.17*k)+r()*16,cy=wh*(.13+.1*((k+1)%2)+r()*.05),R=ww*(k%2?.14:.18);s+=`<g class="fw" data-cx="${f1(cx)}" data-cy="${f1(cy)}" data-r="${f1(R)}" data-off="${f1(k*1.7)}" data-big="${k>=3?1:0}" data-gy="${f1(wh*.6)}"><circle class="rk" r="1.8" fill="#FFE8B0" opacity="0"/>${Array.from({length:28},(_,j)=>`<line class="pt" data-a="${f1(j/28*Math.PI*2)}" data-m="1" stroke="${FC[k][j%2]}" stroke-width="2.6" stroke-linecap="round" opacity="0"/>`).join('')}${Array.from({length:14},(_,j)=>`<line class="pt" data-a="${f1((j+.5)/14*Math.PI*2)}" data-m=".55" stroke="${FC[k][(j+1)%2]}" stroke-width="2.2" stroke-linecap="round" opacity="0"/>`).join('')}<circle class="fl" r="${f1(R*.45)}" fill="${FC[k][0]}" opacity="0"/></g>`}
   /* far trees, then a row of houses with gable roofs, lit windows and small porch flags */
   for(let k=0;k<9;k++){const x=r()*ww,y=wh*.56;s+=`<circle cx="${f1(x)}" cy="${f1(y)}" r="${f1(22+r()*16)}" fill="#0B1424"/>`}
   let x=-16;const HC=['#232C4E','#2A2840','#22304E','#2C263E'];let hk=0;while(x<ww){const w=62+r()*22,h=44+r()*16,base=wh*.74,top=base-h;const c=HC[hk%4];
    s+=`<path d="M${f1(x-4)} ${f1(top)}L${f1(x+w/2)} ${f1(top-26)}L${f1(x+w+4)} ${f1(top)}Z" fill="${dk(c,.35)}" stroke="#3A4466" stroke-width="1.2"/><rect x="${f1(x)}" y="${f1(top)}" width="${f1(w)}" height="${f1(h)}" fill="${c}"/>`;
    [[.18,.3],[.62,.3]].forEach(([fx,fy])=>{if(r()<.75)s+=`<rect x="${f1(x+w*fx)}" y="${f1(top+h*fy)}" width="12" height="11" fill="#FFD98A" opacity="${f1(.55+r()*.4)}"/><rect x="${f1(x+w*fx+5.5)}" y="${f1(top+h*fy)}" width="1.2" height="11" fill="${c}"/>`});
    s+=`<rect x="${f1(x+w*.42)}" y="${f1(base-18)}" width="10" height="18" fill="#3A2A20"/>`;
    if(hk%2===0){const fx=x+w*.86,fy=top+8;s+=`<path d="M${f1(fx)} ${f1(fy)}l14 -10" stroke="#C9C2B4" stroke-width="1.4"/><g class="pflag" data-x="${f1(fx+14)}" data-y="${f1(fy-10)}"><rect x="${f1(fx+5)}" y="${f1(fy-6)}" width="12" height="8" fill="${FRED}" transform="rotate(-35 ${f1(fx+14)} ${f1(fy-10)})"/><rect x="${f1(fx+5)}" y="${f1(fy-6)}" width="5.5" height="4" fill="#2E4FA8" transform="rotate(-35 ${f1(fx+14)} ${f1(fy-10)})"/></g>`}
    x+=w+10+r()*10;hk++}
   /* lawn, pavement and a street lamp */
   s+=`<rect y="${f1(wh*.74)}" width="${ww}" height="${f1(wh*.26)}" fill="#0E2016"/>`;
   let g=`M0 ${f1(groundY(0,ww,wh))}`;for(let xx=0;xx<=ww;xx+=10)g+=`L${xx} ${f1(groundY(xx,ww,wh))}`;g+=`V${wh} H0Z`;s+=`<path d="${g}" fill="#5A6070"/><path d="M0 ${f1(wh-14)} H${ww}" stroke="#3A3F4C" stroke-width="2"/>`;
   const lx=ww*.88;s+=`<circle cx="${f1(lx)}" cy="${f1(wh-120)}" r="46" fill="url(#${id}l)"/><rect x="${f1(lx-2.5)}" y="${f1(wh-122)}" width="5" height="${f1(90)}" fill="#14182A"/><path d="M${f1(lx-9)} ${f1(wh-126)} h18 l-3 10 h-12Z" fill="#14182A"/><rect x="${f1(lx-6)}" y="${f1(wh-120)}" width="12" height="7" fill="#FFE2A0"/>`;
   let ff='';for(let k=0;k<14;k++)ff+=`<circle class="ffly" data-x="${f1(r()*ww)}" data-y="${f1(wh*.7+r()*wh*.16)}" data-d="${f1(r()*9)}" r="2.2" fill="#E8FF8A" opacity="0"/>`;
   return `<rect x="${wx-14}" y="${wy-14}" width="${ww+28}" height="${wh+28}" rx="6" fill="#1A1F33"/><svg x="${wx}" y="${wy}" width="${ww}" height="${wh}" overflow="hidden">${s}${ff}<g class="walkers" data-w="${ww}" data-h="${wh}">${walkers()}</g></svg><rect x="${wx+ww/2-4}" y="${wy}" width="8" height="${wh}" fill="#1A1F33"/><rect x="${wx}" y="${wy+wh/2-4}" width="${ww}" height="8" fill="#1A1F33"/><rect x="${wx-22}" y="${wy+wh+12}" width="${ww+44}" height="10" rx="3" fill="#1A1F33"/>`}

  /* ---------- people walking past (feet on the ground at y=0, facing left) ---------- */
  const skin='#E8B48A',skin2='#B97E58',skin3='#8A5A3A';
  /* a leg with a knee: thigh swings from the hip, shin bends back on the lifted leg */
  function leg(x,hipY,col,shoe,ph,w=6){const L=-hipY;return `<g class="lg" data-x="${x}" data-p="${hipY}" data-ph="${ph}" data-l="${L}"><path class="th" d="" stroke="${col}" stroke-width="${w}" stroke-linecap="round" fill="none"/><path class="sh" d="" fill="${shoe}"/></g>`}
  function face(x,y,r,hair,sk=skin){return `<circle cx="${x}" cy="${y}" r="${r}" fill="${sk}"/><circle cx="${x-r*.45}" cy="${y-r*.1}" r="${r*.13}" fill="#2A1A10"/><path d="M${x-r*.6} ${y+r*.35} q${r*.25} ${r*.2} ${r*.5} 0" stroke="#7A3A20" stroke-width="1.3" fill="none" stroke-linecap="round"/><circle cx="${x-r*.15}" cy="${y+r*.25}" r="${r*.2}" fill="#E07A6A" opacity=".45"/>`+(hair?`<path d="M${x-r} ${y-r*.1} C${x-r} ${y-r*1.3} ${x+r*1.1} ${y-r*1.4} ${x+r} ${y+r*.2} C${x+r*.6} ${y-r*.3} ${x} ${y-r*.6} ${x-r} ${y-r*.1}Z" fill="${hair}"/>`:'')}
  function person(x,o){const H=o.h||1;let s=leg(x-4,-44*H,o.legs,'#15121A',0)+leg(x+4,-44*H,o.legs,'#15121A',1);
   s+=`<path d="M${x-13} ${-90*H} h26 l3 ${46*H} h-32Z" fill="${o.top}"/>`+(o.stripe?`<rect x="${x-13}" y="${-74*H}" width="26" height="5" fill="${o.stripe}"/>`:'');
   s+=face(x,-102*H,11,o.hair,o.skin||skin)+(o.hat||'');return s}
  const cap=(x,y,c)=>`<path d="M${x-11} ${y+2} C${x-11} ${y-12} ${x+11} ${y-12} ${x+11} ${y+2}Z" fill="${c}"/><path d="M${x-11} ${y+1} h-9 a2 2 0 0 0 0 3 h9Z" fill="${c}"/>`;
  function wDog(){/* a dog in a red bandana trotting: legs move in diagonal pairs, body bobs, tail wags */
   const L=(x,ph,c)=>`<g class="dl" data-x="${x}" data-ph="${ph}"><path class="dth" d="" stroke="${c}" stroke-width="5.5" stroke-linecap="round" fill="none"/></g>`;
   let s=L(-22,0,'#8A5A30')+L(14,1,'#8A5A30');
   s+=`<g class="dbody"><path d="M-30 -30 C-30 -42 18 -44 24 -34 C28 -24 20 -18 -2 -18 C-24 -18 -30 -22 -30 -30Z" fill="#C88A4A"/><path d="M-14 -20 C-6 -16 8 -16 16 -20" stroke="#E8C08A" stroke-width="5" fill="none" stroke-linecap="round"/>`+
   `<path class="tail" d="" stroke="#C88A4A" stroke-width="5" stroke-linecap="round" fill="none"/>`+
   `<path d="M-30 -34 C-38 -40 -40 -52 -34 -58 C-28 -64 -16 -62 -14 -52 C-12 -44 -20 -36 -30 -34Z" fill="#C88A4A"/><path d="M-36 -52 C-44 -52 -50 -48 -50 -44 C-50 -40 -44 -39 -38 -40Z" fill="#D9A060"/><circle cx="-50" cy="-45" r="2.6" fill="#1A1010"/><circle cx="-34" cy="-53" r="2" fill="#1A1010"/>`+
   `<path d="M-24 -58 C-22 -66 -16 -66 -16 -60 C-16 -54 -20 -52 -22 -54Z" fill="#7A4A22"/>`+
   `<path d="M-34 -38 L-18 -36 L-26 -26Z" fill="${FRED}"/>${wstar(-25,-32,3,'#fff')}</g>`;
   s+=L(-18,1,'#B07A40')+L(18,0,'#B07A40');
   return `<g class="wk dog" data-off="0" data-s=".85">${s}</g>`}
  function wFlagKid(){/* a kid waving a small flag on a stick */
   let s=leg(-4,-30,'#2E3A5A','#E8E2D4',0,5.5)+leg(4,-30,'#2E3A5A','#E8E2D4',1,5.5);
   s+=`<rect x="-11" y="-62" width="22" height="34" rx="9" fill="${CREAM}"/><rect x="-11" y="-50" width="22" height="5" fill="${FRED}"/><rect x="-11" y="-40" width="22" height="5" fill="${FRED}"/>`;
   s+=`<g class="wave" data-x="-4" data-y="-56"><path d="M-4 -56 L-14 -78" stroke="${CREAM}" stroke-width="7" stroke-linecap="round"/><circle cx="-15" cy="-80" r="3.6" fill="${skin2}"/><path d="M-15 -80 L-17 -118" stroke="#8A6A4A" stroke-width="2"/><path class="wf" d="" fill="${FRED}"/><path class="wf2" d="" fill="#2E4FA8"/></g>`;
   s+=face(0,-72,10,'#2A1A10',skin2)+cap(0,-78,'#2E4FA8');
   return `<g class="wk" data-off="6" data-s="1">${s}</g>`}
  function wDrummer(){/* a kid in a marching-band jacket playing a small drum */
   let s=leg(-4,-32,'#F4F0E8','#15121A',0,5.5)+leg(4,-32,'#F4F0E8','#15121A',1,5.5);
   s+=`<rect x="-11" y="-66" width="22" height="36" rx="8" fill="${FRED}"/><path d="M-10 -64 L10 -36 M10 -64 L-10 -36" stroke="#F4F0E8" stroke-width="3"/>`;
   s+=`<g><rect x="-20" y="-46" width="22" height="16" rx="2" fill="${NAVY}"/><ellipse cx="-9" cy="-46" rx="11" ry="3" fill="#EDE6D6"/><rect x="-21" y="-49" width="24" height="3" fill="#E8B84A"/><rect x="-21" y="-32" width="24" height="3" fill="#E8B84A"/></g>`;
   s+=`<path d="M-3 -63 L-4 -56" stroke="${FRED}" stroke-width="6" stroke-linecap="round"/><path d="M5 -63 L6 -56" stroke="${FRED}" stroke-width="6" stroke-linecap="round"/><circle cx="-4" cy="-55" r="3.2" fill="${skin}"/><circle cx="6" cy="-55" r="3.2" fill="${skin}"/>`;
   s+=`<path class="stk" data-k="0" d="" stroke="#D9A86A" stroke-width="2.4" stroke-linecap="round"/><path class="stk" data-k="1" d="" stroke="#D9A86A" stroke-width="2.4" stroke-linecap="round"/>`;
   s+=face(0,-76,10,null,skin)+`<rect x="-10" y="-102" width="20" height="20" rx="2" fill="${NAVY}"/><rect x="-11" y="-86" width="22" height="4" fill="#E8B84A"/><path d="M-12 -84 h-6 l2 3 h4Z" fill="#111"/><ellipse cx="0" cy="-106" rx="4" ry="6" fill="#F4F0E8"/><circle cx="-4.5" cy="-77" r="1.4" fill="#2A1A10"/>`;
   return `<g class="wk" data-off="11" data-s="1">${s}</g>`}
  function wMelon(){/* someone carrying a whole watermelon in both arms */
   let s=person(0,{legs:'#3A4A6A',top:'#F4F0E8',stripe:'#2E4FA8',hair:'#C89A5A',hat:cap(0,-108,FRED)});
   s+=`<ellipse cx="-12" cy="-66" rx="22" ry="14" fill="#2E7A3A"/>${[-24,-16,-8,0].map(x=>`<path d="M${x-6} -76 Q${x-8} -66 ${x-6} -55" stroke="#1A4A22" stroke-width="3" fill="none"/>`).join('')}<ellipse cx="-18" cy="-71" rx="8" ry="3" fill="#fff" opacity=".18"/>`;
   s+=`<path d="M-8 -84 Q-24 -74 -26 -64" stroke="#F4F0E8" stroke-width="7" stroke-linecap="round" fill="none"/><circle cx="-27" cy="-62" r="3.6" fill="${skin}"/>`;
   return `<g class="wk" data-off="17" data-s=".95">${s}</g>`}
  function wPicnic(){/* a couple: one with a picnic basket, one carrying a folded lawn chair, holding hands */
   let s=person(-16,{legs:'#2A2A3A',top:'#2E4FA8',hair:'#2A1A10',skin:skin3,hat:''});
   s+=`<path d="M-24 -82 L-30 -62" stroke="#2E4FA8" stroke-width="7" stroke-linecap="round"/><g><path d="M-44 -60 h28 l-3 18 h-22Z" fill="#B88A50"/><path d="M-44 -54 h28 M-43 -48 h26" stroke="#8A6030" stroke-width="1.6"/><path d="M-40 -60 Q-30 -76 -20 -60" stroke="#8A6030" stroke-width="2.4" fill="none"/><path d="M-44 -60 h28 v4 h-28Z" fill="${FRED}"/><path d="M-40 -60 v4 M-34 -60 v4 M-28 -60 v4 M-22 -60 v4" stroke="#fff" stroke-width="1.6"/></g>`;
   s+=person(18,{legs:'#1E2440',top:FRED,hair:'#7A4A22',hat:`<ellipse cx="18" cy="-110" rx="17" ry="4" fill="#E8D49A"/><path d="M8 -110 C8 -124 28 -124 28 -110Z" fill="#E8D49A"/><rect x="8" y="-114" width="20" height="3" fill="${NAVY}"/>`});
   s+=`<path d="M28 -84 L36 -60" stroke="${FRED}" stroke-width="7" stroke-linecap="round"/><g transform="rotate(-8 40 -60)"><rect x="34" y="-96" width="6" height="58" rx="2" fill="#E8E2D4"/><rect x="40" y="-92" width="10" height="50" fill="#2E8A9A"/>${[0,1,2,3].map(k=>`<rect x="40" y="${-90+k*12}" width="10" height="5" fill="#F4F0E8"/>`).join('')}<rect x="49" y="-96" width="6" height="58" rx="2" fill="#E8E2D4"/></g>`;
   s+=`<path d="M-8 -72 Q2 -62 10 -72" stroke="${skin3}" stroke-width="5" fill="none" stroke-linecap="round"/>`;
   return `<g class="wk" data-off="23" data-s=".92">${s}</g>`}
  function walkers(){return wDog()+wFlagKid()+wDrummer()+wMelon()+wPicnic()}

  /* ---------- the cookout table (origin = middle of the table top) ---------- */
  const shadowAt=(x,y,rx,ry)=>`<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry??rx*.2}" fill="#000" opacity=".28"/>`;
  const drop=(x,y,r)=>`<ellipse cx="${f1(x)}" cy="${f1(y)}" rx="${f1(r)}" ry="${f1(r*1.25)}" fill="#fff" opacity=".45"/><circle cx="${f1(x-r*.3)}" cy="${f1(y-r*.4)}" r="${f1(r*.3)}" fill="#fff" opacity=".8"/>`;
  function fan(p,x,by){/* a vintage desk fan: heavy base, pole, a cage over three blades; the head turns side to side */
   const hy=by-158,R=60;let s=shadowAt(x,by+2,46,9);
   s+=`<defs><linearGradient id="${p}fm" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#1E5A56"/><stop offset=".4" stop-color="#5FB0A8"/><stop offset="1" stop-color="#174845"/></linearGradient><radialGradient id="${p}fh" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#7CC8BF"/><stop offset="1" stop-color="#1E5A56"/></radialGradient></defs>`;
   s+=`<path d="M${x-44} ${by} C${x-44} ${by-22} ${x+44} ${by-22} ${x+44} ${by}Z" fill="url(#${p}fm)"/><ellipse cx="${x}" cy="${by}" rx="44" ry="7" fill="#123A38"/><rect x="${x-26}" y="${by-14}" width="10" height="5" rx="2" fill="#E8C690"/>`;
   s+=`<rect x="${x-6}" y="${hy+30}" width="12" height="${by-14-hy-30}" fill="url(#${p}fm)"/><path d="M${x-14} ${hy+44} h28 l-4 -16 h-20Z" fill="url(#${p}fm)"/>`;
   s+=`<g class="fanhead" data-x="${x}" data-y="${hy}"><ellipse class="fanback" cx="${x+0}" cy="${hy}" rx="30" ry="28" fill="url(#${p}fh)"/>`;
   s+=`<g class="blades" data-x="${x}" data-y="${hy}">${[0,120,240].map(a=>`<path d="M${x} ${hy} C${x+10} ${hy-14} ${x+42} ${hy-30} ${x+50} ${hy-10} C${x+54} ${hy+4} ${x+26} ${hy+8} ${x} ${hy}Z" fill="#E9E2CF" opacity=".92" stroke="#B8AE96" stroke-width="1" transform="rotate(${a} ${x} ${hy})"/>`).join('')}</g>`;
   s+=`<circle cx="${x}" cy="${hy}" r="${R}" fill="none" stroke="#D9D2BE" stroke-width="4"/>`;[.72,.46].forEach(k=>s+=`<circle cx="${x}" cy="${hy}" r="${f1(R*k)}" fill="none" stroke="#D9D2BE" stroke-width="1.6" opacity=".8"/>`);
   for(let k=0;k<16;k++){const a=k/16*Math.PI*2;s+=`<line x1="${f1(x+Math.cos(a)*10)}" y1="${f1(hy+Math.sin(a)*10)}" x2="${f1(x+Math.cos(a)*R)}" y2="${f1(hy+Math.sin(a)*R)}" stroke="#D9D2BE" stroke-width="1.3" opacity=".85"/>`}
   s+=`<circle cx="${x}" cy="${hy}" r="12" fill="url(#${p}fh)" stroke="#D9D2BE" stroke-width="2"/><circle cx="${x-3}" cy="${hy-3}" r="3" fill="#fff" opacity=".6"/><path d="M${x-R*.7} ${hy-R*.7} A${R} ${R} 0 0 1 ${x+R*.2} ${hy-R}" stroke="#fff" stroke-width="3" fill="none" opacity=".45"/></g>`;
   return s}
  function jarFlags(p,x,by){/* two little cloth flags on sticks standing in a glass jar of water; they flutter when the fan points at them */
   const top=by-74,w=46;let s=shadowAt(x,by+2,30,6);
   const flag=(sx,sy,k)=>`<path d="M${sx} ${by-20} L${sx} ${sy}" stroke="#8A6A4A" stroke-width="3" stroke-linecap="round"/><circle cx="${sx}" cy="${sy-2}" r="3.4" fill="#E8C46A"/><g class="jflag" data-x="${sx}" data-y="${sy+2}" data-k="${k}"><path class="jf" d="" fill="${CREAM}"/>${[0,1,2,3].map(j=>`<path class="jr" data-j="${j}" d="" fill="${FRED}"/>`).join('')}<path class="jc" d="" fill="${NAVY}"/></g>`;
   s+=flag(x-8,by-176,0)+flag(x+10,by-150,1);
   s+=`<defs><linearGradient id="${p}jg" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity=".22"/><stop offset=".2" stop-color="#fff" stop-opacity=".05"/><stop offset=".8" stop-color="#fff" stop-opacity=".05"/><stop offset="1" stop-color="#fff" stop-opacity=".25"/></linearGradient></defs>`;
   s+=`<path d="M${x-w/2} ${top+10} Q${x-w/2} ${top} ${x-w/2+6} ${top} H${x+w/2-6} Q${x+w/2} ${top} ${x+w/2} ${top+10} V${by-6} Q${x+w/2} ${by} ${x+w/2-6} ${by} H${x-w/2+6} Q${x-w/2} ${by} ${x-w/2} ${by-6}Z" fill="#9FC6D8" fill-opacity=".14" stroke="#CFE4EE" stroke-opacity=".6" stroke-width="1.6"/>`;
   s+=`<rect x="${x-w/2+3}" y="${top+26}" width="${w-6}" height="${by-top-29}" rx="5" fill="#7FB0C8" opacity=".25"/><path d="M${x-w/2+3} ${top+26} H${x+w/2-3}" stroke="#CFE4EE" stroke-width="1.4" opacity=".7"/>`;
   s+=`<rect x="${x-w/2}" y="${top}" width="${w}" height="${by-top}" rx="6" fill="url(#${p}jg)"/><rect x="${x-w/2-2}" y="${top-8}" width="${w+4}" height="10" rx="3" fill="#B9B6AE"/><path d="M${x-w/2} ${top-4} H${x+w/2}" stroke="#E8E4DA" stroke-width="1.4"/>`;
   return s}
  function flagCake(p,x,by){/* a round cake on a white cake stand, decorated as a flag: blueberries in one corner, strawberry rows */
   const pt=by-40,rx=60,ry=15,ct=pt-50;let s=shadowAt(x,by+2,40,7);
   s+=`<defs><linearGradient id="${p}cs" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#C9C2B4"/><stop offset=".4" stop-color="#FFFFFF"/><stop offset="1" stop-color="#B5AE9F"/></linearGradient><clipPath id="${p}ct"><ellipse cx="${x}" cy="${ct}" rx="${rx-2}" ry="${ry-1.5}"/></clipPath></defs>`;
   s+=`<ellipse cx="${x}" cy="${by-3}" rx="34" ry="8" fill="url(#${p}cs)"/><path d="M${x-8} ${by-6} L${x-6} ${pt+4} H${x+6} L${x+8} ${by-6}Z" fill="url(#${p}cs)"/><ellipse cx="${x}" cy="${pt+3}" rx="${rx+16}" ry="${ry+4}" fill="#B5AE9F"/><ellipse cx="${x}" cy="${pt}" rx="${rx+16}" ry="${ry+4}" fill="url(#${p}cs)"/>`;
   s+=`<path d="M${x-rx} ${ct} V${pt-4} A${rx} ${ry} 0 0 0 ${x+rx} ${pt-4} V${ct}Z" fill="url(#${p}cs)"/>`;
   for(let k=0;k<13;k++){const t=-1.35+k*2.7/12,xx=x+Math.sin(t)*rx,yy=pt-4+Math.cos(t)*ry;s+=`<circle cx="${f1(xx)}" cy="${f1(yy-2)}" r="4.4" fill="#FFFFFF" stroke="#D8D2C4" stroke-width=".8"/>`}
   s+=`<ellipse cx="${x}" cy="${ct}" rx="${rx}" ry="${ry}" fill="#FBF8F2" stroke="#E2DCCF" stroke-width="1"/>`;
   let top='';const r=rnd(5);for(let i=0;i<34;i++){const u=r(),v=r();const xx=x-rx+6+u*rx*.85,yy=ct-ry+3+v*ry*.95;if(((xx-x)/rx)**2+((yy-ct)/ry)**2<.86)top+=`<circle cx="${f1(xx)}" cy="${f1(yy)}" r="3.4" fill="#2A3A8A"/><circle cx="${f1(xx-1)}" cy="${f1(yy-1)}" r="1" fill="#9FB0E8"/>`}
   [[-2,.0],[0,.42],[2,.84]].forEach(([row,o],i)=>{const yy=ct-ry*.55+i*ry*.5;for(let k=0;k<7;k++){const xx=x+6+(i?-rx*.9:0)+k*(i? rx*.28: rx*.13)+ (i?0:0);if(!i&&k>3)continue;if(((xx-x)/rx)**2+((yy-ct)/ry)**2<.9)top+=`<path d="M${f1(xx-4.5)} ${f1(yy-2)} Q${f1(xx)} ${f1(yy-5)} ${f1(xx+4.5)} ${f1(yy-2)} Q${f1(xx+3)} ${f1(yy+4)} ${f1(xx)} ${f1(yy+5)} Q${f1(xx-3)} ${f1(yy+4)} ${f1(xx-4.5)} ${f1(yy-2)}Z" fill="#D8283A"/><path d="M${f1(xx-3)} ${f1(yy-2.6)} h6" stroke="#3A8A3A" stroke-width="1.6"/>`}});
   s+=`<g clip-path="url(#${p}ct)">${top}</g>`;
   for(let k=0;k<15;k++){const t=-1.4+k*2.8/14,xx=x+Math.sin(t)*rx,yy=ct+Math.cos(t)*ry;s+=`<circle cx="${f1(xx)}" cy="${f1(yy)}" r="3.6" fill="#FFFFFF" stroke="#D8D2C4" stroke-width=".8"/>`}
   return s}
  function lemon(x,y,r){let s=`<circle cx="${f1(x)}" cy="${f1(y)}" r="${r}" fill="#F2D33A"/><circle cx="${f1(x)}" cy="${f1(y)}" r="${f1(r*.84)}" fill="#FBEFA0"/>`;for(let k=0;k<8;k++){const a=k/8*Math.PI*2;s+=`<line x1="${f1(x)}" y1="${f1(y)}" x2="${f1(x+Math.cos(a)*r*.8)}" y2="${f1(y+Math.sin(a)*r*.8)}" stroke="#F2D33A" stroke-width="1"/>`}return s}
  function iceCube(x,y,s,rot){return `<g transform="rotate(${rot} ${x} ${y})"><rect x="${f1(x-s)}" y="${f1(y-s)}" width="${f1(s*2)}" height="${f1(s*2)}" rx="${f1(s*.3)}" fill="#EAF6FF" fill-opacity=".55" stroke="#fff" stroke-opacity=".7" stroke-width="1"/><path d="M${f1(x-s*.6)} ${f1(y-s*.5)} h${f1(s*.7)}" stroke="#fff" stroke-width="1.4" opacity=".8"/></g>`}
  function pitcher(p,x,by){/* a glass pitcher of lemonade with lemon slices and ice, cold drops on the outside, bubbles rising */
   const top=by-128,w1=40,w2=50;let s=shadowAt(x,by+2,48,8);
   const body=`M${x-w1} ${top} L${x-w2} ${by-14} Q${x-w2} ${by} ${x-w2+12} ${by} H${x+w2-12} Q${x+w2} ${by} ${x+w2} ${by-14} L${x+w1} ${top}Z`;
   s+=`<defs><clipPath id="${p}pc"><path d="${body}"/></clipPath><linearGradient id="${p}lm" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#E8C850"/><stop offset=".4" stop-color="#FFF0A8"/><stop offset="1" stop-color="#D8B040"/></linearGradient></defs>`;
   s+=`<path d="M${x+w1-2} ${top+18} C${x+w1+38} ${top+14} ${x+w1+40} ${top+78} ${x+w2-6} ${top+92}" stroke="#DCEBF2" stroke-opacity=".55" stroke-width="9" fill="none"/>`;
   s+=`<g clip-path="url(#${p}pc)"><rect x="${x-w2}" y="${top+26}" width="${w2*2}" height="${by-top}" fill="url(#${p}lm)" opacity=".92"/><ellipse cx="${x}" cy="${top+26}" rx="${w1+2}" ry="5" fill="#FFF6C8"/>`+lemon(x-16,top+56,15)+lemon(x+14,top+86,14)+iceCube(x+16,top+36,10,14)+iceCube(x-14,top+32,9,-10)+iceCube(x+2,top+40,8,30);
   s+=`<g class="bub" data-x="${x}" data-top="${top+30}" data-bot="${by-10}">${Array.from({length:8},(_,k)=>`<circle data-k="${k}" r="${f1(1.4+(k%3)*.6)}" fill="#fff" opacity=".8"/>`).join('')}</g></g>`;
   s+=`<path d="${body}" fill="#DCEBF2" fill-opacity=".08" stroke="#E6F2F8" stroke-opacity=".75" stroke-width="2"/><path d="M${x-w1} ${top} Q${x-w1-10} ${top-6} ${x-w1-14} ${top-12}" stroke="#E6F2F8" stroke-opacity=".75" stroke-width="2" fill="none"/><ellipse cx="${x}" cy="${top}" rx="${w1}" ry="5" fill="none" stroke="#E6F2F8" stroke-opacity=".8" stroke-width="2"/>`;
   s+=`<path d="M${x-w1+8} ${top+12} L${x-w2+10} ${by-18}" stroke="#fff" stroke-width="4" opacity=".35" stroke-linecap="round"/>`;
   const r=rnd(9);for(let k=0;k<14;k++)s+=drop(x-w1+r()*w1*2,top+30+r()*(by-top-44),1.4+r()*1.4);
   return s}
  function tumbler(p,x,by){const top=by-70,w1=24,w2=20;let s=shadowAt(x,by+2,26,5);
   s+=`<g><path d="M${x+4} ${top+20} L${x+20} ${top-34}" stroke="#F4F0E8" stroke-width="7" stroke-linecap="round"/>`;for(let k=0;k<5;k++)s+=`<path d="M${f1(x+5+k*3.2)} ${f1(top+16-k*10.5)} l3 -5" stroke="${FRED}" stroke-width="4"/>`;s+=`</g>`;
   s+=`<path d="M${x-w1} ${top} L${x-w2} ${by} H${x+w2} L${x+w1} ${top}Z" fill="#DCEBF2" fill-opacity=".1"/><path d="M${x-w1+2} ${top+14} L${x-w2+1} ${by-2} H${x+w2-1} L${x+w1-2} ${top+14}Z" fill="#F5E07A" opacity=".85"/>`+iceCube(x-6,top+22,7,12);
   s+=`<path d="M${x-w1} ${top} L${x-w2} ${by} H${x+w2} L${x+w1} ${top}" fill="none" stroke="#E6F2F8" stroke-opacity=".75" stroke-width="2"/><ellipse cx="${x}" cy="${top}" rx="${w1}" ry="4" fill="none" stroke="#E6F2F8" stroke-opacity=".8" stroke-width="1.6"/>`;
   s+=`<path d="M${x-w1-2} ${top+2} a12 12 0 0 1 20 -6 Z" fill="#F2D33A"/><path d="M${x-w1} ${top+1} a10 10 0 0 1 16 -5" stroke="#FBEFA0" stroke-width="2" fill="none"/>`;
   const r=rnd(12);for(let k=0;k<5;k++)s+=drop(x-w1+4+r()*(w1*2-8),top+18+r()*44,1.3+r());return s}
  function melonPlate(p,x,by){/* three watermelon slices standing on their rinds on a white plate */
   let s=shadowAt(x,by+3,66,12)+`<ellipse cx="${x}" cy="${by}" rx="66" ry="14" fill="#E8E4DA"/><ellipse cx="${x}" cy="${by-2}" rx="52" ry="10" fill="#F6F3EC"/>`;
   [[-30,-8,-6],[0,-4,0],[28,-6,8]].forEach(([dx,dy,rot],i)=>{const cx=x+dx,cy=by+dy,R=34;s+=`<g transform="rotate(${rot} ${cx} ${cy})"><path d="M${cx-R} ${cy} A${R} ${R} 0 0 1 ${cx+R} ${cy}Z" fill="#2E7A3A"/><path d="M${cx-R+5} ${cy} A${R-5} ${R-5} 0 0 1 ${cx+R-5} ${cy}Z" fill="#E8F2D0"/><path d="M${cx-R+8} ${cy} A${R-8} ${R-8} 0 0 1 ${cx+R-8} ${cy}Z" fill="#E8394A"/><path d="M${cx-R+8} ${cy} A${R-8} ${R-8} 0 0 1 ${cx} ${cy-R+8}" stroke="#FF6A78" stroke-width="3" fill="none" opacity=".6"/>`;
    [[-12,-10],[0,-16],[12,-10],[-6,-6],[6,-6]].forEach(([sx,sy])=>s+=`<ellipse cx="${cx+sx}" cy="${cy+sy}" rx="1.6" ry="2.6" fill="#1A1010" transform="rotate(${sx*2} ${cx+sx} ${cy+sy})"/>`);s+=`</g>`});return s}
  function tableScene(sc=1){const p='tb'+(RID++);const RX=250,RY=46;
   let s=`<defs><pattern id="${p}gh" width="24" height="24" patternUnits="userSpaceOnUse"><rect width="24" height="24" fill="#F4F0E8"/><rect width="12" height="24" fill="${FRED}" opacity=".55"/><rect width="24" height="12" fill="${FRED}" opacity=".55"/></pattern><linearGradient id="${p}fold" x1="0" y1="0" x2="1" y2="0">${Array.from({length:13},(_,k)=>`<stop offset="${f1(k/12)}" stop-color="#000" stop-opacity="${k%2?.0:.28}"/>`).join('')}</linearGradient><linearGradient id="${p}sd" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity=".1"/><stop offset="1" stop-color="#000" stop-opacity=".45"/></linearGradient><linearGradient id="${p}leg" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#2A1A10"/><stop offset=".5" stop-color="#6A4228"/><stop offset="1" stop-color="#2A1A10"/></linearGradient></defs>`;
   s+=`<ellipse cx="0" cy="214" rx="${RX+20}" ry="26" fill="#000" opacity=".4"/>`;
   [-170,170].forEach(lx=>s+=`<rect x="${lx-8}" y="120" width="16" height="92" fill="url(#${p}leg)"/>`);
   /* tablecloth: gingham top seen at an angle, skirt hanging in folds with a wavy hem */
   let hem=`M${-RX} 0 L${-RX-8} 132`;for(let k=0;k<=12;k++){const xx=-RX-8+k*(2*RX+16)/12;hem+=`Q${f1(xx-(2*RX+16)/24)} ${k%2?146:136} ${f1(xx)} ${k%2?132:140}`}hem+=`L${RX} 0Z`;
   s+=`<path d="${hem}" fill="url(#${p}gh)"/><path d="${hem}" fill="url(#${p}fold)"/><path d="${hem}" fill="url(#${p}sd)"/>`;
   s+=`<g transform="translate(0 0) scale(1 ${RY/RX})"><circle r="${RX}" fill="url(#${p}gh)"/></g><ellipse rx="${RX}" ry="${RY}" fill="#000" opacity=".08"/><ellipse rx="${RX}" ry="${RY}" fill="none" stroke="#000" stroke-opacity=".2" stroke-width="2"/>`;
   s+=`<ellipse rx="${RX}" ry="${RY}" fill="url(#${p}glow)" opacity=".6"/><defs><radialGradient id="${p}glow" cx=".3" cy=".2" r=".9"><stop offset="0" stop-color="#FFE0A0" stop-opacity=".35"/><stop offset="1" stop-color="#FFE0A0" stop-opacity="0"/></radialGradient></defs>`;
   s+=fan(p,-170,-12)+jarFlags(p,-82,-16)+flagCake(p,28,-20)+pitcher(p,140,-8)+melonPlate(p,-92,24)+tumbler(p,200,18);
   return `<g class="tscene" transform="scale(${sc})">${s}</g>`}


  function legPath(l,ph){const x=+l.dataset.x,hy=+l.dataset.p,L=+l.dataset.l;const a=Math.sin(ph)*.42,b=Math.max(0,-Math.cos(ph))*.95;const t=L*.5;
   const kx=x+Math.sin(a)*t,ky=hy+Math.cos(a)*t,fx=kx+Math.sin(a+b)*t,fy=ky+Math.cos(a+b)*t;return [`M${f1(x)} ${f1(hy)}L${f1(kx)} ${f1(ky)}L${f1(fx)} ${f1(fy)}`,fx,fy]}
  function flagQuad(sx,sy,W,H,ang,amp,t,k){return (u0,u1,v0,v1)=>{const pt=(u,v)=>{const ca=Math.cos(ang),sa=Math.sin(ang);const w=amp*Math.sin(u*6-t*14+k)*u;return [sx+u*W*ca-(v*H+w)*sa,sy+u*W*sa+(v*H+w)*ca]};
   let d='';const N=8;for(let i=0;i<=N;i++){const q=pt(u0+(u1-u0)*i/N,v0);d+=(i?'L':'M')+f1(q[0])+' '+f1(q[1])}for(let i=N;i>=0;i--){const q=pt(u0+(u1-u0)*i/N,v1);d+='L'+f1(q[0])+' '+f1(q[1])}return d+'Z'}}

  /** The two shapes: wide (TVs, computers) and tall (phones, no window) */
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1590, 140, 280, 350], table: [350, 712, 1] },
    tall: { w: 390, h: 844, win: null, table: [200, 712, 0.5] },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t) } which poses everything at t seconds.
   * opts: { layout: "wide"|"tall", variant: "day"|"night", align: "xMidYMax slice" }
   */
  function build(svg, opts) {
    const L = LAYOUTS[(opts && opts.layout) || "wide"];
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", (opts && opts.align) || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win);
    svg.innerHTML = rm.bg + `<g transform="translate(${L.table[0]} ${L.table[1]})">${tableScene(L.table[2])}</g>` + rm.vig;
    return pose(svg, (opts && opts.variant) || variant(new Date()));
  }

  /* gathers every moving part, and returns at(t) */
  function pose(root, mode) {
    const q = (s) => [...root.querySelectorAll(s)];
    const big = mode === "night";
    const bubs = q(".bub").map((g) => ({ x: +g.dataset.x, top: +g.dataset.top, bot: +g.dataset.bot, c: [...g.children] }));
    const fans = q(".fanhead").map((g) => ({ g, x: +g.dataset.x, y: +g.dataset.y, bl: g.querySelector(".blades"), bk: g.querySelector(".fanback"), fl: [...g.closest(".tscene").querySelectorAll(".jflag")] }));
    const pens = q(".pen"), ffs = q(".ffly"), pfl = q(".pflag"), flash = q(".flash");
    const fws = q(".fw").map((g) => ({ g, cx: +g.dataset.cx, cy: +g.dataset.cy, R: +g.dataset.r, off: +g.dataset.off, big: +g.dataset.big, gy: +g.dataset.gy, rk: g.querySelector(".rk"), fl: g.querySelector(".fl"), col: g.querySelector(".fl").getAttribute("fill"), pts: [...g.querySelectorAll(".pt")].map((c) => ({ c, a: +c.dataset.a, m: +c.dataset.m })) }));
    const walkers = q(".walkers").map((W) => ({ ww: +W.dataset.w, wh: +W.dataset.h, wk: [...W.querySelectorAll(".wk")] }));
    // speeds that fit whole turns into one loop, so the video joins up
    const sFan = wq(0.45), sPen = wq(1.1), sPfl = wq(2), sFf = wq(1.2), sFx = wq(0.4), sFy = wq(0.6), sBub = wq(3), sFlag = wq(14);
    // the fan's three blades look the same every 120 degrees: 33 000 degrees is 275 of those in one loop
    const bladeDeg = 33000 / LOOP;
    function at(t) {
      bubs.forEach((B) => B.c.forEach((c) => {
        const k = +c.dataset.k; const a = ((t * 12 / LOOP + k / 8) % 1);
        c.setAttribute("cx", f1(B.x + Math.sin(k * 2.3) * 18 + Math.sin(t * sBub + k) * 1)); c.setAttribute("cy", f1(B.bot - (B.bot - B.top) * a)); c.setAttribute("opacity", a > 0.94 ? "0" : "0.8");
      }));
      fans.forEach((F) => {
        const th = Math.sin(t * sFan) * 0.95; const sc = Math.max(0.32, Math.cos(th));
        F.g.setAttribute("transform", `translate(${F.x} ${F.y}) scale(${sc.toFixed(3)} 1) translate(${-F.x} ${-F.y})`); F.bk.setAttribute("cx", f1(F.x - Math.sin(th) * 34 / sc));
        F.bl.setAttribute("transform", `rotate(${f1((t * bladeDeg) % 360)} ${F.x} ${F.y})`);
        const s = Math.max(0, Math.sin(th)) * 0.85 + 0.1;
        F.fl.forEach((g) => {
          const sx = +g.dataset.x, sy = +g.dataset.y, k = +g.dataset.k; const ang = (1 - s) * 1.2 + 0.05; const Q = flagQuad(sx, sy, 40, 24, ang, 3.5 * s + 0.6, t * sFlag / 14, k * 1.7);
          g.querySelector(".jf").setAttribute("d", Q(0, 1, 0, 1)); g.querySelectorAll(".jr").forEach((r) => { const j = +r.dataset.j; r.setAttribute("d", Q(0, 1, j * 2 / 7, (j * 2 + 1) / 7)); }); g.querySelector(".jc").setAttribute("d", Q(0, 0.42, 0, 4 / 7));
        });
      });
      pens.forEach((g) => { const k = +g.dataset.k; g.setAttribute("transform", `rotate(${f1(Math.sin(t * sPen + k * 0.7) * 3)} ${g.dataset.x} ${g.dataset.y})`); });
      pfl.forEach((g, k) => g.setAttribute("transform", `rotate(${f1(Math.sin(t * sPfl + k) * 6)} ${g.dataset.x} ${g.dataset.y})`));
      ffs.forEach((c) => { const d = +c.dataset.d; const o = Math.pow(Math.max(0, Math.sin(t * sFf + d)), 6); c.setAttribute("opacity", o.toFixed(2)); c.setAttribute("cx", f1(+c.dataset.x + Math.sin(t * sFx + d) * 10)); c.setAttribute("cy", f1(+c.dataset.y + Math.sin(t * sFy + d * 2) * 6)); });
      // fireworks: the odd one over the roofs, or one after another on the night of the 4th; the room glows with each
      let fv = 0, fc = "#fff";
      fws.forEach((F) => {
        if (!big && F.big) { F.g.setAttribute("opacity", "0"); return; }
        F.g.setAttribute("opacity", "1"); const P = big ? 2.5 : 6; const a = ((t + F.off) % P) / P; const up = big ? 0.22 : 0.12;
        if (a < up) {
          const e = a / up; F.rk.setAttribute("cx", f1(F.cx + Math.sin(e * 6) * 2)); F.rk.setAttribute("cy", f1(F.gy - (F.gy - F.cy) * e)); F.rk.setAttribute("opacity", "1"); F.pts.forEach((p) => p.c.setAttribute("opacity", "0")); F.fl.setAttribute("opacity", "0");
        } else {
          F.rk.setAttribute("opacity", "0"); const e = Math.min(1, (a - up) / (big ? 0.7 : 0.4)); const rr = F.R * (1 - Math.pow(1 - e, 3));
          F.fl.setAttribute("cx", f1(F.cx)); F.fl.setAttribute("cy", f1(F.cy)); F.fl.setAttribute("opacity", e < 0.12 ? ((0.12 - e) * 2).toFixed(2) : "0");
          if (e < 0.3) { const v = (0.3 - e) * (big ? 0.32 : 0.12); if (v > fv) { fv = v; fc = F.col; } }
          F.pts.forEach((p) => { const r2 = rr * p.m, r1 = r2 * (0.55 + 0.4 * e), dr = e * e * F.R * 0.45 * p.m; p.c.setAttribute("x1", f1(F.cx + Math.cos(p.a) * r1)); p.c.setAttribute("y1", f1(F.cy + Math.sin(p.a) * r1 + dr * 0.7)); p.c.setAttribute("x2", f1(F.cx + Math.cos(p.a) * r2)); p.c.setAttribute("y2", f1(F.cy + Math.sin(p.a) * r2 + dr)); p.c.setAttribute("opacity", (e >= 1 ? 0 : Math.min(1, (1 - e) * 1.4)).toFixed(2)); });
        }
      });
      flash.forEach((f) => { f.setAttribute("opacity", fv.toFixed(3)); f.setAttribute("fill", fc); });
      // people walking past every 30 seconds (each is off the window for most of the loop, so it joins up)
      walkers.forEach(({ ww, wh, wk }) => {
        const v = (ww + 300) / 9;
        wk.forEach((g) => {
          const off = +g.dataset.off, sc = 1.45 * (+g.dataset.s); const tt = ((t + LOOP - off) % LOOP); const x = ww + 150 - tt * v; const isDog = g.classList.contains("dog"); const sp = isDog ? 11 : 7;
          const y = groundY(x, ww, wh) + 1; const bob = Math.abs(Math.sin(tt * sp)) * (isDog ? 2 : 1.6);
          g.setAttribute("transform", `translate(${f1(x)} ${f1(y - bob)}) scale(${sc})`);
          g.querySelectorAll(".lg").forEach((l) => { const [d, fx, fy] = legPath(l, tt * sp + (+l.dataset.ph) * Math.PI); l.querySelector(".th").setAttribute("d", d); l.querySelector(".sh").setAttribute("d", `M${f1(fx + 3)} ${f1(fy + 1.5)} h-9 a3 3 0 0 1 0 -5 h7Z`); });
          g.querySelectorAll(".dl").forEach((l) => { const x0 = +l.dataset.x, ph = tt * sp + (+l.dataset.ph) * Math.PI; const a = Math.sin(ph) * 0.5, b = Math.max(0, -Math.cos(ph)) * 0.9; const kx = x0 + Math.sin(a) * 12, ky = -24 + Math.cos(a) * 12, fx = kx + Math.sin(a + b) * 12, fy = ky + Math.cos(a + b) * 12; l.querySelector(".dth").setAttribute("d", `M${x0} -26L${f1(kx)} ${f1(ky)}L${f1(fx)} ${f1(fy)}`); });
          const tl = g.querySelector(".tail"); if (tl) { const w = Math.sin(tt * 16) * 7; tl.setAttribute("d", `M22 -32 Q32 -36 ${f1(34 + w * 0.3)} ${f1(-46 + w)}`); }
          const wv = g.querySelector(".wave"); if (wv) { wv.setAttribute("transform", `rotate(${f1(Math.sin(tt * 5) * 14)} -4 -56)`); const Q = flagQuad(-17, -118, 22, 14, -0.1 + Math.sin(tt * 5) * 0.15, 2.5, tt, 0); wv.querySelector(".wf").setAttribute("d", Q(0, 1, 0, 1)); wv.querySelector(".wf2").setAttribute("d", Q(0, 0.42, 0, 0.55)); }
          g.querySelectorAll(".stk").forEach((s) => { const k = +s.dataset.k; const px = k ? 6 : -4, py = -55; const up = Math.max(0, Math.sin(tt * 10 + k * Math.PI)); s.setAttribute("d", `M${px} ${py}L${f1(-16 + k * 8)} ${f1(-47 - up * 9)}`); });
        });
      });
    }
    at(0);
    return { at };
  }

  /** Plays a built room live until the element leaves the page */
  function play(svg, scene) {
    const start = performance.now() - 4000;
    const tick = (now) => { if (!svg.isConnected) return; scene.at((now - start) / 1000); requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
  }

  G.BenflixJuly4 = { LOOP, season, variant, greeting, countdown, build, play, LAYOUTS };
})(typeof window !== "undefined" ? window : globalThis);
