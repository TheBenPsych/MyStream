/* Benflix New Year's: the Who's watching party room (approved 2026-11-17, thread "Design a New Year's theme for Benflix").
 *
 * A midnight-blue room with warm globe string lights. The party table sits at the bottom left: champagne in a
 * silver ice bucket, two flutes with rising bubbles, a gold carriage clock with a ticking second hand, a party
 * hat, a rolled-up blowout horn, confetti and curly ribbons, and gold foil balloons of the new year's number
 * tied to a small gold weight. A window on the right shows a snowy city with fireworks over the roofs and
 * people walking past (a couple in party hats holding hands, a kid with a sparkler, someone carrying three
 * balloons and a man in a top hat blowing a party horn). 27 to 31 December the bottle is corked; from
 * midnight and all of 1 January ("day") it's open, the cork lies on the table, streamers hang off the table
 * and confetti falls all day.
 *
 * Like feast.js, the same drawing is used live on the web and by tools/newyear/record.js, which records it as
 * looping videos for the TV and phone app. Everything is a function of the time t (seconds) and repeats
 * every LOOP seconds, so a video of one loop joins up with no jump.
 * The drawing code comes straight from the approved design sheet (newyear-3-table.html).
 */
(function (G) {
  "use strict";
  /** 60 seconds, so the clock's second hand goes all the way round once */
  const LOOP = 60;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;
  /** a rate that fits a whole number of cycles into one loop, close to r cycles a second */
  const rq = (r) => Math.max(1, Math.round(r * LOOP)) / LOOP;

  // ---------- dates ----------
  /** 27 December to 1 January (Christmas ends on the 26th) */
  const season = (d) => (d.getMonth() === 11 && d.getDate() >= 27) || (d.getMonth() === 0 && d.getDate() === 1);
  /** the year being welcomed */
  const yearFor = (d) => (d.getMonth() === 11 ? d.getFullYear() + 1 : d.getFullYear());
  /** "eve" 27 to 31 December (bottle corked), "day" all of 1 January (opened, confetti all day) */
  const variant = (d) => (d.getMonth() === 0 ? "day" : "eve");
  /** seconds left in the 10-second countdown to midnight on 31 December, else 0 */
  const countdownSecs = (d) => {
    if (d.getMonth() !== 11 || d.getDate() !== 31 || d.getHours() !== 23 || d.getMinutes() !== 59 || d.getSeconds() < 50) return 0;
    return 60 - d.getSeconds();
  };
  /** the big words: "Happy New Year", the countdown numbers, then "Happy 2027!" all of New Year's Day */
  const greeting = (d) => {
    const c = countdownSecs(d);
    if (c) return String(c);
    return variant(d) === "day" ? `Happy ${yearFor(d)}!` : "Happy New Year";
  };
  /** the line under the greeting, up to New Year's Eve: "4 days to 2027" */
  const countdown = (d) => {
    if (d.getMonth() !== 11 || countdownSecs(d)) return "";
    const days = 32 - d.getDate();
    return days <= 1 ? `${yearFor(d)} starts tonight at midnight` : `${days} days to ${yearFor(d)}`;
  };

  // ---------- drawing kit (from the approved design sheet) ----------
  let UID=0;const u=p=>p+(UID++)+'_';
  const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
  const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
  const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
  const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
  const f1=v=>(+v).toFixed(1);
  const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
    const MID='#22335C',GOLDC='#E8B84A';
    /** the year on the balloons (set by build) */
    let YEAR=2027;
  function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
  function smooth(P,closed){const n=P.length;let d=`M${f1(P[0][0])} ${f1(P[0][1])}`;const g=i=>closed?P[(i+n)%n]:P[Math.max(0,Math.min(n-1,i))];
   for(let i=0;i<(closed?n:n-1);i++){const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);d+=`C${f1(p1[0]+(p2[0]-p0[0])/6)} ${f1(p1[1]+(p2[1]-p0[1])/6)} ${f1(p2[0]-(p3[0]-p1[0])/6)} ${f1(p2[1]-(p3[1]-p1[1])/6)} ${f1(p2[0])} ${f1(p2[1])}`}return d+(closed?'Z':'')}
  function flameD(bx,by,w,h,t,ph,sp=1){const P=[];const N=7;
   for(let i=0;i<=N;i++){const s=i/N;const wob=Math.sin(t*5.2*sp+ph+s*3.1)*w*.16*s;const hw=w/2*Math.pow(1-s,.85)*(1+.25*Math.sin(s*Math.PI));P.push([bx-hw+wob,by-s*h])}
   const tipx=bx+Math.sin(t*4.1*sp+ph)*w*.2,tipy=by-h*(1.04+.08*Math.sin(t*6.3*sp+ph*2));
   const R=[];for(let i=N;i>=0;i--){const s=i/N;const wob=Math.sin(t*5.2*sp+ph+s*3.1)*w*.16*s;const hw=w/2*Math.pow(1-s,.85)*(1+.25*Math.sin(s*Math.PI));R.push([bx+hw+wob,by-s*h])}
   return smooth([...P.slice(0,N),[tipx,tipy],...R.slice(1)],true)}
  let RID=0;
  function groundY(x,ww,wh){return wh-40+Math.sin(x*.02)*3}
  /* ---------- the room: midnight-blue walls, warm string lights ---------- */
  function room(W2,H2,win,lights=true){const id='rm'+(RID++);const fl=H2*.8;
   let s=`<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#090C18"/><stop offset="1" stop-color="#121830"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1E1712"/><stop offset="1" stop-color="#0A0807"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".6"/></radialGradient><radialGradient id="${id}h"><stop offset="0" stop-color="#FFD58A" stop-opacity=".9"/><stop offset=".35" stop-color="#FFC060" stop-opacity=".25"/><stop offset="1" stop-color="#FFC060" stop-opacity="0"/></radialGradient></defs>`;
   s+=`<rect width="${W2}" height="${H2}" fill="url(#${id}w)"/>`;for(let x=0;x<W2;x+=W2/24)s+=`<rect x="${f1(x)}" y="0" width="${f1(W2/48)}" height="${fl}" fill="#fff" opacity=".014"/>`;
   s+=`<rect y="${f1(fl-H2*.16)}" width="${W2}" height="${f1(H2*.16)}" fill="#000" opacity=".2"/><rect y="${f1(fl-H2*.16)}" width="${W2}" height="4" fill="#2A3352" opacity=".8"/>`;
   if(win)s+=windowSVG(win);
   s+=`<rect y="${fl}" width="${W2}" height="${H2-fl}" fill="url(#${id}f)"/>`;for(let k=1;k<6;k++)s+=`<path d="M0 ${f1(fl+(H2-fl)*k/6)} H${W2}" stroke="#000" stroke-opacity=".25" stroke-width="2"/>`;
   s+=`<rect y="${fl-10}" width="${W2}" height="10" fill="#0C0F1C"/>`;
   if(lights){/* a string of warm globe bulbs swagged along the top of the wall, two swags */const sc=W2/1920;const swags=W2<600?[[0,22,W2,22,22]]:[[300*sc,36*sc,800*sc,36*sc,96*sc],[800*sc,36*sc,1300*sc,36*sc,96*sc]];
    swags.forEach(([x1,y1,x2,y2,sag])=>{const N=Math.round((x2-x1)/(W2<600?34:48*sc));const pt=k=>{const t=k/N;return [x1+(x2-x1)*t,y1+(y2-y1)*t+sag*4*t*(1-t)]};
     let wire='';for(let k=0;k<=N;k++){const [x,y]=pt(k);wire+=(k?'L':'M')+f1(x)+' '+f1(y)}s+=`<path d="${wire}" stroke="#1A1A14" stroke-width="${f1(3*Math.max(sc,.5))}" fill="none"/>`;
     for(let k=1;k<N;k++){const [x,y]=pt(k);const r=(W2<600?7:11*sc);s+=`<rect x="${f1(x-r*.35)}" y="${f1(y)}" width="${f1(r*.7)}" height="${f1(r*.6)}" fill="#3A3226"/><circle class="bulbh" data-k="${k}" cx="${f1(x)}" cy="${f1(y+r*1.6)}" r="${f1(r*3.2)}" fill="url(#${id}h)"/><circle cx="${f1(x)}" cy="${f1(y+r*1.6)}" r="${f1(r)}" fill="#FFE8B8"/><circle cx="${f1(x-r*.3)}" cy="${f1(y+r*1.3)}" r="${f1(r*.3)}" fill="#fff"/>`}})}
   return {bg:s,vig:`<rect width="${W2}" height="${H2}" fill="url(#${id}v)"/>`}}

  /* ---------- the window: a city on New Year's Eve, snow on the street, fireworks over the roofs ---------- */
  function windowSVG([wx,wy,ww,wh]){const id='wn'+(RID++);const r=rnd(77);
   let s=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#060A1C"/><stop offset=".7" stop-color="#18234A"/><stop offset="1" stop-color="#2E3C6E"/></linearGradient><radialGradient id="${id}l"><stop offset="0" stop-color="#FFD98A" stop-opacity=".6"/><stop offset="1" stop-color="#FFD98A" stop-opacity="0"/></radialGradient></defs><rect width="${ww}" height="${wh}" fill="url(#${id}s)"/>`;
   for(let k=0;k<26;k++)s+=`<circle cx="${f1(r()*ww)}" cy="${f1(r()*wh*.4)}" r="${f1(.6+r()*.9)}" fill="#fff" opacity="${f1(.3+r()*.5)}"/>`;
   /* fireworks live behind the roofs */
   const FC=[['#FFE08A','#FFFFFF'],['#DDE6F5','#9FD0FF'],['#FFB8C8','#FFE08A']];
   for(let k=0;k<3;k++){const cx=ww*(.22+.3*k)+r()*20,cy=wh*(.16+.1*((k+1)%2)),R=ww*.17;s+=`<g class="fw" data-cx="${f1(cx)}" data-cy="${f1(cy)}" data-r="${f1(R)}" data-off="${f1(k*1.4)}" data-gy="${f1(wh*.62)}"><circle class="rk" r="1.8" fill="#FFE8B0"/>${Array.from({length:26},(_,j)=>`<circle class="pt" data-a="${f1(j/26*Math.PI*2)}" r="2" fill="${FC[k][j%2]}"/>`).join('')}<circle class="fl" r="${f1(R*.9)}" fill="${FC[k][0]}" opacity="0"/></g>`}
   /* two rows of buildings, far and near, with lit windows */
   [[.5,'#0E1430',.55],[.62,'#070A18',.85]].forEach(([base,col,lit],row)=>{let x=-10;while(x<ww){const w=22+r()*34,h=wh*(.18+r()*.26)*(row?1:1.2),y=wh*base+wh*.25-h;s+=`<rect x="${f1(x)}" y="${f1(y)}" width="${f1(w)}" height="${f1(wh-y)}" fill="${col}"/>`;
    for(let yy=y+6;yy<wh-50;yy+=10)for(let xx=x+4;xx<x+w-6;xx+=8)if(r()<lit*.4)s+=`<rect x="${f1(xx)}" y="${f1(yy)}" width="3.4" height="4.6" fill="#FFD98A" opacity="${f1(.35+r()*.55)}"/>`;x+=w+2+r()*6}});
   /* snowy pavement and a street lamp */
   let g=`M0 ${f1(groundY(0,ww,wh))}`;for(let x=0;x<=ww;x+=10)g+=`L${x} ${f1(groundY(x,ww,wh))}`;g+=`V${wh} H0Z`;
   s+=`<path d="${g}" fill="#C9D2E4"/><path d="M0 ${f1(wh-22)} H${ww}" stroke="#9AA6C0" stroke-width="2" opacity=".6"/>`;
   const lx=ww*.86;s+=`<circle cx="${f1(lx)}" cy="${f1(wh-128)}" r="46" fill="url(#${id}l)"/><rect x="${f1(lx-2.5)}" y="${f1(wh-130)}" width="5" height="${f1(90)}" fill="#14182A"/><path d="M${f1(lx-9)} ${f1(wh-134)} h18 l-3 10 h-12Z" fill="#14182A"/><rect x="${f1(lx-6)}" y="${f1(wh-128)}" width="12" height="7" fill="#FFE2A0"/>`;
   let snow='';for(let k=0;k<26;k++)snow+=`<circle class="sn" data-x="${f1(r()*ww)}" data-y="${f1(r()*wh)}" data-v="${f1(10+r()*14)}" data-d="${f1(r()*6)}" data-wh="${wh}" r="${f1(1+r()*1.4)}" fill="#fff" opacity=".8"/>`;
   return `<rect x="${wx-14}" y="${wy-14}" width="${ww+28}" height="${wh+28}" rx="6" fill="#1A1F33"/><svg x="${wx}" y="${wy}" width="${ww}" height="${wh}" overflow="hidden">${s}<g class="walkers" data-w="${ww}" data-h="${wh}">${walkers()}</g>${snow}</svg><rect x="${wx+ww/2-4}" y="${wy}" width="8" height="${wh}" fill="#1A1F33"/><rect x="${wx}" y="${wy+wh/2-4}" width="${ww}" height="8" fill="#1A1F33"/><rect x="${wx-22}" y="${wy+wh+12}" width="${ww+44}" height="10" rx="3" fill="#1A1F33"/>`}

  /* ---------- people walking past (feet on the ground at y=0, facing left) ---------- */
  const skin='#E8B48A',skin2='#B97E58';
  function leg(x,hipY,col,shoe,ph){return `<g class="lg" data-x="${x}" data-p="${hipY}" data-ph="${ph}"><path d="M${x} ${hipY} L${x} -3" stroke="${col}" stroke-width="6" stroke-linecap="round"/><path d="M${x+2} -1 h-8 a3 3 0 0 1 0 -5 h6Z" fill="${shoe}" stroke="${shoe}" stroke-width="2" stroke-linejoin="round"/></g>`}
  function face(x,y,r,hair,sk=skin){return `<circle cx="${x}" cy="${y}" r="${r}" fill="${sk}"/><circle cx="${x-r*.45}" cy="${y-r*.1}" r="${r*.13}" fill="#2A1A10"/><path d="M${x-r*.6} ${y+r*.35} q${r*.25} ${r*.2} ${r*.5} 0" stroke="#7A3A20" stroke-width="1.3" fill="none" stroke-linecap="round"/><circle cx="${x-r*.15}" cy="${y+r*.25}" r="${r*.2}" fill="#E07A6A" opacity=".45"/>`+(hair?`<path d="M${x-r} ${y-r*.1} C${x-r} ${y-r*1.3} ${x+r*1.1} ${y-r*1.4} ${x+r} ${y+r*.2} C${x+r*.6} ${y-r*.3} ${x} ${y-r*.6} ${x-r} ${y-r*.1}Z" fill="${hair}"/>`:'')}
  const partyHat=(x,y,h,c1,c2)=>`<path d="M${x-h*.32} ${y} L${x} ${y-h} L${x+h*.32} ${y}Z" fill="${c1}"/><path d="M${x-h*.2} ${y-h*.38} L${x+h*.2} ${y-h*.38} L${x+h*.13} ${y-h*.6} L${x-h*.13} ${y-h*.6}Z" fill="${c2}"/><circle cx="${x}" cy="${y-h}" r="${h*.14}" fill="${c2}"/>`;
  function person(x,o){/* a grown-up: coat, scarf, legs; o.hat draws on top */
   const H=o.h||1;let s=leg(x-5,-44*H,o.legs,'#15121A',0)+leg(x+5,-44*H,o.legs,'#15121A',1);
   s+=`<path d="M${x-14} ${-90*H} h28 l5 ${48*H} h-38Z" fill="${o.coat}"/><path d="M${x} ${-90*H} v${46*H}" stroke="#000" stroke-opacity=".18" stroke-width="2"/>`;
   s+=`<rect x="${x-12}" y="${-92*H}" width="24" height="7" rx="3.5" fill="${o.scarf}"/>`;
   s+=face(x,-102*H,11,o.hair,o.skin||skin)+(o.hat||'');return s}
  function wCouple(){/* two people holding hands, both in party hats */
   let s=person(-16,{legs:'#2A2A3A',coat:'#7A1E3A',scarf:GOLDC,hair:'#2A1A10',hat:partyHat(-16,-110,30,'#E8B84A','#1B1B22')});
   s+=person(18,{legs:'#1E2440',coat:'#2E4A7A',scarf:'#E8E2D4',hair:'#7A4A22',skin:skin2,hat:partyHat(18,-110,30,'#C9D2DE','#E8B84A')});
   s+=`<path d="M-8 -72 Q2 -60 10 -72" stroke="${skin}" stroke-width="5" fill="none" stroke-linecap="round"/>`;
   return `<g class="wk" data-off="0" data-s=".92">${s}</g>`}
  function wSparkler(){/* a kid holding up a lit sparkler */
   let s=leg(-4,-30,'#2E3A5A','#4A2A18',0)+leg(4,-30,'#2E3A5A','#4A2A18',1);
   s+=`<rect x="-11" y="-62" width="22" height="34" rx="9" fill="#2E7A6A"/>`;
   s+=`<path d="M-4 -56 L-14 -76" stroke="#2A6A5A" stroke-width="7" stroke-linecap="round"/><circle cx="-15" cy="-78" r="3.6" fill="${skin}"/><path d="M-15 -78 L-22 -104" stroke="#8A8A90" stroke-width="1.6"/>`;
   s+=`<g class="spk" data-x="-23" data-y="-106">${Array.from({length:12},(_,k)=>`<path class="sp" data-k="${k}" d="" stroke="${k%3?'#FFE08A':'#fff'}" stroke-width="1.4" stroke-linecap="round"/>`).join('')}<circle cx="-23" cy="-106" r="7" fill="#FFE9A0" opacity=".4"/><circle cx="-23" cy="-106" r="2.6" fill="#fff"/></g>`;
   s+=face(0,-72,10,'#5A3418')+`<path d="M-11 -74 C-11 -88 11 -88 11 -74Z" fill="#E84A5A"/><rect x="-12" y="-76" width="24" height="5" rx="2.5" fill="#fff"/><circle cx="0" cy="-86" r="4" fill="#fff"/>`;
   return `<g class="wk" data-off="8" data-s=".95">${s}</g>`}
  function wBalloons(){/* someone carrying three balloons on strings */
   let s=person(0,{legs:'#3A2A20',coat:'#5A3A6A',scarf:'#E8B84A',hair:'#C89A5A',hat:`<path d="M-11 -106 C-11 -122 11 -122 11 -106Z" fill="#E8E2D4"/>`});
   s+=`<path d="M-6 -84 L-16 -64" stroke="#4A2E5A" stroke-width="7" stroke-linecap="round"/><circle cx="-17" cy="-62" r="3.6" fill="${skin}"/>`;
   s+=`<g class="bls">${[[-34,-168,'#E8B84A'],[-12,-182,'#C9D2DE'],[-50,-190,'#3A5AA8']].map(([x,y,c])=>`<path d="M-17 -62 Q${(x-17)/2} ${(y-62)/2} ${x} ${y+16}" stroke="#E8E2D4" stroke-width="1" fill="none"/><ellipse cx="${x}" cy="${y}" rx="13" ry="16" fill="${c}"/><ellipse cx="${x-4}" cy="${y-6}" rx="3.4" ry="5" fill="#fff" opacity=".5"/><path d="M${x-2} ${y+16} l2 -3 l2 3Z" fill="${c}"/>`).join('')}</g>`;
   return `<g class="wk" data-off="15" data-s=".9">${s}</g>`}
  function wTophat(){/* a man in a top hat and long coat, blowing a party horn */
   let s=person(0,{h:1.05,legs:'#14141C',coat:'#1C1C26',scarf:'#E8E2D4',hair:'#3A2A1A',hat:`<rect x="-11" y="-138" width="22" height="24" rx="2" fill="#0E0E14"/><rect x="-11" y="-120" width="22" height="4" fill="#E8B84A"/><rect x="-16" y="-116" width="32" height="4" rx="2" fill="#0E0E14"/>`});
   s+=`<path d="M-8 -86 L-16 -100" stroke="#14141C" stroke-width="7" stroke-linecap="round"/><circle cx="-15" cy="-101" r="3.4" fill="${skin}"/>`;
   s+=`<path class="horn" d="" stroke="#E8B84A" stroke-width="5" fill="none" stroke-linecap="round"/><path class="horn2" d="" stroke="#E84A5A" stroke-width="2" fill="none" stroke-linecap="round"/>`;
   return `<g class="wk" data-off="22" data-s=".92">${s}</g>`}
  function walkers(){return wCouple()+wSparkler()+wBalloons()+wTophat()}
  /* ---------- the party table ---------- */
  function goldDefs(p){return `<linearGradient id="${p}g" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#8A6418"/><stop offset=".35" stop-color="#F6DA86"/><stop offset=".6" stop-color="#D9A940"/><stop offset="1" stop-color="#7A5410"/></linearGradient>
   <linearGradient id="${p}s" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#5E6A7C"/><stop offset=".3" stop-color="#F4F7FB"/><stop offset=".55" stop-color="#B8C2D0"/><stop offset=".8" stop-color="#E8EDF4"/><stop offset="1" stop-color="#5E6A7C"/></linearGradient>
   <linearGradient id="${p}c" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FCE7A6"/><stop offset="1" stop-color="#D9A040"/></linearGradient>
   <linearGradient id="${p}b" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#0C2414"/><stop offset=".35" stop-color="#2E5A3A"/><stop offset=".5" stop-color="#16361F"/><stop offset="1" stop-color="#06120A"/></linearGradient>
   <linearGradient id="${p}d" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFF4C8"/><stop offset=".45" stop-color="#EBC35A"/><stop offset="1" stop-color="#9A6E1C"/></linearGradient>
   <radialGradient id="${p}gl"><stop offset="0" stop-color="#FFC870" stop-opacity=".35"/><stop offset="1" stop-color="#FFC870" stop-opacity="0"/></radialGradient>`}
  function numberBalloons(p,xs,y,size){/* gold foil number balloons spelling the new year; each has its own string down to the table edge */
   return String(YEAR).split('').map((d,k)=>{const x=xs[k];return `<g class="numb" data-k="${k}" data-x="${x}" data-y="${y}"><text x="${x}" y="${y}" text-anchor="middle" font-family="Outfit,sans-serif" font-weight="800" font-size="${size}" fill="url(#${p}d)" stroke="url(#${p}d)" stroke-width="${f1(size*.12)}" stroke-linejoin="round" paint-order="stroke">${d}</text>
   <text x="${x}" y="${y}" text-anchor="middle" font-family="Outfit,sans-serif" font-weight="800" font-size="${size}" fill="none" stroke="#7A5410" stroke-width="2" opacity=".6">${d}</text>
   <text x="${x-4}" y="${y-4}" text-anchor="middle" font-family="Outfit,sans-serif" font-weight="800" font-size="${size}" fill="none" stroke="#FFF8DC" stroke-width="2.4" opacity=".55" stroke-dasharray="14 40">${d}</text>
   <path d="M${x-3} ${y+size*.1} l3 6 l3 -6Z" fill="#9A6E1C"/></g>`}).join('')}
  function confetti(W,H,n){const r=rnd(19);const CC=['#E8B84A','#F4F7FB','#3A5AA8','#E84A5A','#C9D2DE','#FFE08A'];return `<g class="conf" opacity="0">${Array.from({length:n},(_,k)=>`<rect data-x="${f1(r()*W)}" data-v="${f1(.5+r()*.7)}" data-d="${f1(r()*6)}" data-h="${H}" width="${f1(W/150)}" height="${f1(W/320)}" fill="${CC[k%6]}"/>`).join('')}</g>`}
  let STATE='eve';/* 'eve' = 27-31 Dec (bottle corked), 'day' = from midnight and all of 1 Jan (popped) */
  const drop=(x,y,r)=>`<ellipse cx="${f1(x)}" cy="${f1(y)}" rx="${f1(r)}" ry="${f1(r*1.25)}" fill="#fff" opacity=".22"/><ellipse cx="${f1(x-r*.3)}" cy="${f1(y-r*.4)}" rx="${f1(r*.35)}" ry="${f1(r*.4)}" fill="#fff" opacity=".85"/><path d="M${f1(x-r*.7)} ${f1(y+r*.6)} q${f1(r*.7)} ${f1(r*.6)} ${f1(r*1.4)} 0" stroke="#000" stroke-opacity=".25" stroke-width=".8" fill="none"/>`;
  function itemDefs(p){return `<linearGradient id="${p}ag" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#3E4656"/><stop offset=".12" stop-color="#9AA4B4"/><stop offset=".26" stop-color="#FFFFFF"/><stop offset=".38" stop-color="#C6CED9"/><stop offset=".55" stop-color="#7E8898"/><stop offset=".72" stop-color="#E4E9F0"/><stop offset=".86" stop-color="#9AA4B4"/><stop offset="1" stop-color="#3E4656"/></linearGradient>
   <linearGradient id="${p}lip" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset=".45" stop-color="#B8C2D0"/><stop offset="1" stop-color="#5E6A7C"/></linearGradient>
   <linearGradient id="${p}gb" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#03100A"/><stop offset=".18" stop-color="#1F4A2C"/><stop offset=".26" stop-color="#6FA07A"/><stop offset=".31" stop-color="#1F4A2C"/><stop offset=".7" stop-color="#0E2A18"/><stop offset=".84" stop-color="#2E5A3A"/><stop offset="1" stop-color="#020805"/></linearGradient>
   <linearGradient id="${p}fo" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#6A4A10"/><stop offset=".22" stop-color="#E8C468"/><stop offset=".32" stop-color="#FFF4C8"/><stop offset=".45" stop-color="#D9A940"/><stop offset=".8" stop-color="#9A6E1C"/><stop offset="1" stop-color="#4A320A"/></linearGradient>
   <linearGradient id="${p}gw" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity=".26"/><stop offset=".2" stop-color="#fff" stop-opacity=".05"/><stop offset=".75" stop-color="#fff" stop-opacity=".03"/><stop offset="1" stop-color="#fff" stop-opacity=".22"/></linearGradient>
   <linearGradient id="${p}ch" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#B8862C"/><stop offset=".3" stop-color="#F6DA86"/><stop offset=".55" stop-color="#EBC35A"/><stop offset="1" stop-color="#A87420"/></linearGradient>
   <linearGradient id="${p}cv" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFF0BC"/><stop offset="1" stop-color="#E0AE48"/></linearGradient>
   <linearGradient id="${p}hat" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#000" stop-opacity=".55"/><stop offset=".35" stop-color="#000" stop-opacity="0"/><stop offset=".5" stop-color="#fff" stop-opacity=".18"/><stop offset=".75" stop-color="#000" stop-opacity=".1"/><stop offset="1" stop-color="#000" stop-opacity=".6"/></linearGradient>
   <radialGradient id="${p}ice" cx=".35" cy=".3" r=".9"><stop offset="0" stop-color="#FFFFFF" stop-opacity=".95"/><stop offset=".6" stop-color="#CFE2F4" stop-opacity=".8"/><stop offset="1" stop-color="#8FB0D0" stop-opacity=".75"/></radialGradient>
   <filter id="${p}sb" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="4"/></filter>`}
  const shadowAt=(p,x,y,rx,ry=null)=>`<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry??rx*.2}" fill="#000" opacity=".5" filter="url(#${p}sb)"/>`;
  function iceCube(p,x,y,s,rot){return `<g transform="rotate(${rot} ${x} ${y})"><path d="M${x-s} ${y-s*.35} L${x-s*.1} ${y-s*.8} L${x+s} ${y-s*.45} L${x+s} ${y+s*.5} L${x+s*.05} ${y+s*.9} L${x-s} ${y+s*.45}Z" fill="url(#${p}ice)" stroke="#fff" stroke-opacity=".8" stroke-width="1"/><path d="M${x-s} ${y-s*.35} L${x+s*.05} ${y} L${x+s} ${y-s*.45} M${x+s*.05} ${y} V${y+s*.9}" stroke="#fff" stroke-opacity=".6" stroke-width="1" fill="none"/><path d="M${x-s} ${y-s*.35} L${x-s*.1} ${y-s*.8} L${x+s} ${y-s*.45} L${x+s*.05} ${y}Z" fill="#fff" opacity=".45"/><ellipse cx="${x-s*.35}" cy="${y+s*.2}" rx="${s*.18}" ry="${s*.08}" fill="#fff" opacity=".7"/></g>`}
  function bottle(p,x,by,top){/* champagne bottle standing in the bucket; only shoulder, neck and foil show above the ice */
   const B=`M${x-28} ${by} V${top-46} C${x-28} ${top-78} ${x-12} ${top-92} ${x-11} ${top-120} V${top-182} H${x+11} V${top-120} C${x+12} ${top-92} ${x+28} ${top-78} ${x+28} ${top-46} V${by}Z`;
   let s=`<path d="${B}" fill="url(#${p}gb)"/>`;
   /* main label just peeking above the ice */
   s+=`<path d="M${x-28} ${top+6} V${top-14} Q${x} ${top-20} ${x+28} ${top-14} V${top+6}Z" fill="#F4ECD8"/><path d="M${x-28} ${top-12} Q${x} ${top-18} ${x+28} ${top-12}" stroke="#C99A30" stroke-width="2.4" fill="none"/>`;
   /* neck medallion */
   s+=`<ellipse cx="${x}" cy="${top-62}" rx="17" ry="12" fill="#F4ECD8" stroke="#C99A30" stroke-width="2.4"/><ellipse cx="${x}" cy="${top-62}" rx="11" ry="7" fill="none" stroke="#C99A30" stroke-width="1"/><path d="M${x-5} ${top-62} h10 M${x} ${top-67} v10" stroke="#9A6E1C" stroke-width="1.2"/>`;
   if(STATE==='eve'){/* gold foil over the cork, crinkled, with the wire cage top */
    s+=`<path d="M${x-12} ${top-116} C${x-16} ${top-98} ${x-22} ${top-92} ${x-24} ${top-86} Q${x} ${top-80} ${x+24} ${top-86} C${x+22} ${top-92} ${x+16} ${top-98} ${x+12} ${top-116} V${top-186} Q${x} ${top-190} ${x-12} ${top-186}Z" fill="url(#${p}fo)"/>`;
    s+=[[-8,-160,-150],[-3,-176,-128],[4,-168,-138],[8,-150,-120],[-6,-132,-104]].map(([dx,a,b])=>`<path d="M${x+dx} ${top+a} l${f1(dx*.15)} ${b-a}" stroke="#6A4A10" stroke-width="1" opacity=".55"/>`).join('');
    s+=`<path d="M${x-24} ${top-86} Q${x} ${top-80} ${x+24} ${top-86}" stroke="#FFF4C8" stroke-width="1.6" fill="none" opacity=".7"/><rect x="${x-13}" y="${top-194}" width="26" height="9" rx="3.5" fill="url(#${p}fo)"/><ellipse cx="${x}" cy="${top-194}" rx="13" ry="3" fill="#F6DA86"/>`;
   }else{/* opened: foil torn back, cork gone, a wisp of cold mist */
    s+=`<path d="M${x-12} ${top-116} C${x-16} ${top-98} ${x-22} ${top-92} ${x-24} ${top-86} Q${x} ${top-80} ${x+24} ${top-86} C${x+22} ${top-92} ${x+16} ${top-98} ${x+12} ${top-116} L${x+12} ${top-146} L${x+6} ${top-138} L${x} ${top-150} L${x-6} ${top-140} L${x-12} ${top-148}Z" fill="url(#${p}fo)"/>`;
    s+=`<ellipse cx="${x}" cy="${top-182}" rx="11" ry="3" fill="#06140C" stroke="#2E5A3A" stroke-width="2"/><g class="mist">${[0,1,2].map(k=>`<path class="ms" data-k="${k}" data-x="${x}" data-y="${top-186}" d="" stroke="#fff" stroke-width="3" fill="none" stroke-linecap="round" opacity=".3"/>`).join('')}</g>`}
   /* glass shine and cold droplets */
   s+=`<path d="M${x-17} ${by-10} V${top-50} C${x-17} ${top-70} ${x-9} ${top-84} ${x-7} ${top-110}" stroke="#fff" stroke-opacity=".5" stroke-width="4" fill="none" stroke-linecap="round"/><path d="M${x+19} ${by-10} V${top-46}" stroke="#fff" stroke-opacity=".18" stroke-width="2.5"/>`;
   s+=[[-10,-30,2.2],[12,-38,1.8],[-2,-46,1.6],[16,-24,2]].map(([dx,dy,r])=>drop(x+dx,top+dy,r)).join('');
   return s}
  function iceBucket(p,x,by){const top=by-112,bw=60,tw=76;
   let s=shadowAt(p,x,by+2,tw*.95,10);
   s+=`<ellipse cx="${x}" cy="${top}" rx="${tw}" ry="15" fill="#151A26"/>`;
   s+=`<g transform="rotate(8 ${x} ${by-30})">${bottle(p,x,by-20,top)}</g>`;
   /* ice heaped up in the bucket, in front of the bottle */
   [[-56,-2,13,-14],[-36,-8,14,10],[-14,2,12,-6],[22,-6,14,18],[46,0,13,-12],[62,4,10,8],[-60,6,10,4],[4,8,11,22],[34,10,12,-20]].forEach(([dx,dy,sz,rt])=>{s+=iceCube(p,x+dx,top+dy,sz,rt)});
   /* body, rolled lip, base band */
   s+=`<path d="M${x-tw+3} ${top+4} L${x-bw} ${by-8} Q${x} ${by+6} ${x+bw} ${by-8} L${x+tw-3} ${top+4} Q${x} ${top+20} ${x-tw+3} ${top+4}Z" fill="url(#${p}ag)"/>`;
   s+=`<path d="M${x-bw} ${by-8} Q${x} ${by+6} ${x+bw} ${by-8} L${x+bw+2} ${by-1} Q${x} ${by+14} ${x-bw-2} ${by-1}Z" fill="url(#${p}ag)" stroke="#3E4656" stroke-width=".8"/>`;
   s+=`<path d="M${x-tw} ${top} Q${x} ${top+17} ${x+tw} ${top}" stroke="url(#${p}lip)" stroke-width="8" fill="none" stroke-linecap="round"/><path d="M${x-tw+6} ${top-1} Q${x} ${top+14} ${x+tw-6} ${top-1}" stroke="#fff" stroke-width="1.6" fill="none" opacity=".8"/>`;
   s+=`<path d="M${x-tw+10} ${top+40} Q${x} ${top+56} ${x+tw-10} ${top+40}" stroke="#5E6A7C" stroke-width="1.6" fill="none" opacity=".5"/><path d="M${x-tw+12} ${top+44} Q${x} ${top+60} ${x+tw-12} ${top+44}" stroke="#fff" stroke-width="1.2" fill="none" opacity=".5"/>`;
   /* warm reflections of the string lights */
   s+=`<ellipse cx="${x-34}" cy="${top+30}" rx="4" ry="2.4" fill="#FFE2A0" opacity=".9"/><ellipse cx="${x+40}" cy="${top+26}" rx="3" ry="2" fill="#FFE2A0" opacity=".7"/>`;
   /* lion-ring handles on round mounts */
   [-1,1].forEach(sd=>{const hx=x+sd*(tw-6),hy=top+26;s+=`<circle cx="${hx}" cy="${hy}" r="7" fill="url(#${p}lip)" stroke="#3E4656" stroke-width=".8"/><ellipse cx="${hx+sd*5}" cy="${hy+12}" rx="9" ry="12" fill="none" stroke="#3E4656" stroke-width="5"/><ellipse cx="${hx+sd*5}" cy="${hy+12}" rx="9" ry="12" fill="none" stroke="url(#${p}lip)" stroke-width="3"/>`});
   /* condensation */
   const r=rnd(31);for(let k=0;k<22;k++){const dx=(r()-.5)*2*(bw-4),dy=20+r()*80;s+=drop(x+dx,top+dy,1.2+r()*1.8)}
   return s}
  function flute(p,x,by,tilt){const cid=p+'fl'+x;const lt=by-138;
   const bowl=`M${x-4} ${by-66} C${x-17} ${by-92} ${x-19} ${by-134} ${x-17} ${by-170} L${x+17} ${by-170} C${x+19} ${by-134} ${x+17} ${by-92} ${x+4} ${by-66}Z`;
   let s=shadowAt(p,x,by+1,22,5);
   s+=`<g transform="rotate(${tilt} ${x} ${by})"><defs><clipPath id="${cid}"><path d="${bowl}"/></clipPath><linearGradient id="${cid}w" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#C9922E"/><stop offset=".35" stop-color="#F8E2A0"/><stop offset=".7" stop-color="#E6B654"/><stop offset="1" stop-color="#B07E22"/></linearGradient></defs>`;
   /* foot and stem */
   s+=`<ellipse cx="${x}" cy="${by-2}" rx="21" ry="5.5" fill="#fff" opacity=".14"/><ellipse cx="${x}" cy="${by-2}" rx="21" ry="5.5" fill="none" stroke="#fff" stroke-opacity=".75" stroke-width="1.4"/><path d="M${x-14} ${by-1} Q${x} ${by+3} ${x+14} ${by-1}" stroke="#FFE2A0" stroke-opacity=".55" stroke-width="1.4" fill="none"/>`;
   s+=`<path d="M${x-2.6} ${by-4} C${x-1.6} ${by-30} ${x-1.6} ${by-50} ${x-3} ${by-64} L${x+3} ${by-64} C${x+1.6} ${by-50} ${x+1.6} ${by-30} ${x+2.6} ${by-4}Z" fill="#fff" opacity=".22"/><path d="M${x-1} ${by-6} V${by-62}" stroke="#fff" stroke-opacity=".85" stroke-width="1"/><ellipse cx="${x}" cy="${by-60}" rx="4" ry="3" fill="#fff" opacity=".35"/>`;
   /* the glass bowl, the champagne inside it, a thin mousse on top */
   s+=`<path d="${bowl}" fill="url(#${p}gw)"/><g clip-path="url(#${cid})"><rect x="${x-22}" y="${lt}" width="44" height="76" fill="url(#${cid}w)" opacity=".95"/><rect x="${x-22}" y="${lt}" width="44" height="76" fill="url(#${p}gw)" opacity=".6"/>
   <rect x="${x-22}" y="${lt-3}" width="44" height="6" fill="#FFF8E4" opacity=".9"/>${[...Array(9)].map((_,k)=>`<circle cx="${x-15+k*3.8}" cy="${lt-2+(k%2)*1.6}" r="1.4" fill="#fff"/>`).join('')}
   <g class="bub" data-x="${x}" data-top="${lt+4}" data-bot="${by-70}">${Array.from({length:9},(_,k)=>`<circle data-k="${k}" r="${f1(.9+(k%3)*.45)}" fill="#fff" opacity=".85"/>`).join('')}</g></g>`;
   s+=`<path d="${bowl}" fill="none" stroke="#fff" stroke-opacity=".7" stroke-width="1.3"/><ellipse cx="${x}" cy="${by-170}" rx="17" ry="3.2" fill="none" stroke="#fff" stroke-opacity=".9" stroke-width="1.6"/>`;
   s+=`<path d="M${x-12} ${by-158} C${x-14} ${by-128} ${x-12} ${by-98} ${x-5} ${by-76}" stroke="#fff" stroke-opacity=".75" stroke-width="2.6" fill="none" stroke-linecap="round"/><path d="M${x+11} ${by-150} C${x+12} ${by-130} ${x+11} ${by-110} ${x+8} ${by-96}" stroke="#fff" stroke-opacity=".3" stroke-width="1.6" fill="none" stroke-linecap="round"/><circle cx="${x-8}" cy="${by-162}" r="2" fill="#FFE8B8"/>`;
   return s+`</g>`}
  function carriageClock(p,x,by){const w=70,h=92,top=by-h,cy=by-48;
   let s=shadowAt(p,x,by+1,44,7);
   /* plinth with little bun feet, frame, corner columns, cornice and handle */
   s+=`<rect x="${x-w/2-6}" y="${by-10}" width="${w+12}" height="9" rx="2" fill="url(#${p}ch)" stroke="#6A4A10" stroke-width=".8"/>${[-1,1].map(sd=>`<ellipse cx="${x+sd*(w/2)}" cy="${by}" rx="6" ry="3.4" fill="url(#${p}ch)" stroke="#6A4A10" stroke-width=".8"/>`).join('')}`;
   s+=`<rect x="${x-w/2}" y="${top+10}" width="${w}" height="${h-20}" fill="#2A2416"/><rect x="${x-w/2+8}" y="${top+14}" width="${w-16}" height="${h-28}" fill="#F6EED8"/>`;
   [-1,1].forEach(sd=>{const cx=x+sd*(w/2-4);s+=`<rect x="${cx-5}" y="${top+8}" width="10" height="${h-16}" rx="4" fill="url(#${p}ch)" stroke="#6A4A10" stroke-width=".8"/><path d="M${cx-1.6} ${top+12} V${by-12}" stroke="#FFF4C8" stroke-width="1.6" opacity=".8"/>`});
   s+=`<rect x="${x-w/2-6}" y="${top+2}" width="${w+12}" height="10" rx="2" fill="url(#${p}ch)" stroke="#6A4A10" stroke-width=".8"/><rect x="${x-w/2-2}" y="${top-4}" width="${w+4}" height="7" rx="2" fill="url(#${p}ch)" stroke="#6A4A10" stroke-width=".8"/>`;
   s+=`<path d="M${x-20} ${top-3} C${x-22} ${top-28} ${x+22} ${top-28} ${x+20} ${top-3}" stroke="#6A4A10" stroke-width="6" fill="none"/><path d="M${x-20} ${top-3} C${x-22} ${top-28} ${x+22} ${top-28} ${x+20} ${top-3}" stroke="url(#${p}ch)" stroke-width="4" fill="none"/>`;
   /* the dial: enamel face, Roman hours, gold hands, red seconds */
   s+=`<circle cx="${x}" cy="${cy}" r="24" fill="#FFFDF4" stroke="url(#${p}ch)" stroke-width="3.6"/>`;
   [['XII',0],['III',90],['VI',180],['IX',270]].forEach(([n,d])=>{const a=d*Math.PI/180;s+=`<text x="${f1(x+Math.sin(a)*15)}" y="${f1(cy-Math.cos(a)*15+3)}" font-family="Georgia,serif" font-size="8" font-weight="700" fill="#2A2418" text-anchor="middle">${n}</text>`});
   for(let k=0;k<12;k++)if(k%3){const a=k/12*Math.PI*2;s+=`<circle cx="${f1(x+Math.sin(a)*17)}" cy="${f1(cy-Math.cos(a)*17)}" r="1.1" fill="#2A2418"/>`}
   const hand=(deg,len,w2)=>{const a=deg*Math.PI/180;return `<path d="M${x} ${cy} L${f1(x+Math.sin(a)*len)} ${f1(cy-Math.cos(a)*len)}" stroke="#1A1410" stroke-width="${w2}" stroke-linecap="round"/>`};
   const hh=STATE==='day'?10:-4,mm=STATE==='day'?60:-30;
   s+=hand(hh,11,3.2)+hand(mm,18,2)+`<line class="secd" x1="${x}" y1="${cy+4}" x2="${x}" y2="${cy-20}" stroke="#B8202A" stroke-width="1.1" data-x="${x}" data-y="${cy}"/><circle cx="${x}" cy="${cy}" r="2.2" fill="url(#${p}ch)"/>`;
   /* bevelled glass reflection across the front */
   s+=`<path d="M${x-w/2+8} ${top+40} L${x-w/2+30} ${top+14} L${x-w/2+40} ${top+14} L${x-w/2+8} ${top+52}Z" fill="#fff" opacity=".28"/><path d="M${x+w/2-22} ${by-14} L${x+w/2-8} ${by-30} L${x+w/2-8} ${by-24} L${x+w/2-16} ${by-14}Z" fill="#fff" opacity=".18"/>`;
   return s}
  function tableHat(p,x,by){const h=84,bw=30;const cid=p+'hc';
   let s=shadowAt(p,x+4,by+2,34,6);
   s+=`<defs><clipPath id="${cid}"><path d="M${x-bw} ${by} L${x} ${by-h} L${x+bw} ${by} Q${x} ${by+9} ${x-bw} ${by}Z"/></clipPath></defs>`;
   s+=`<path d="M${x-bw} ${by} L${x} ${by-h} L${x+bw} ${by} Q${x} ${by+9} ${x-bw} ${by}Z" fill="#14151E"/>`;
   /* gold foil stripes that curve round the cone */
   s+=`<g clip-path="url(#${cid})">${[0,1,2,3,4].map(k=>{const y=by-6-k*17,c=k/5;return `<path d="M${x-40} ${y-8} Q${x} ${y+4-c*6} ${x+40} ${y-8} L${x+40} ${y-1} Q${x} ${y+11-c*6} ${x-40} ${y-1}Z" fill="url(#${p}d)"/>`}).join('')}<rect x="${x-bw}" y="${by-h}" width="${bw*2}" height="${h+10}" fill="url(#${p}hat)"/></g>`;
   s+=`<path d="M${x-bw} ${by} Q${x} ${by+9} ${x+bw} ${by}" stroke="#F6DA86" stroke-width="2.6" fill="none"/>`;
   /* elastic cord lying on the table */
   s+=`<path d="M${x-bw+3} ${by+1} C${x-bw-10} ${by+12} ${x+6} ${by+16} ${x+bw-3} ${by+1}" stroke="#E8E2D4" stroke-width="1.2" fill="none" opacity=".8"/>`;
   /* tinsel pom-pom */
   const r=rnd(44);let pp='';for(let k=0;k<40;k++){const a=r()*Math.PI*2,l=7+r()*11;pp+=`<path d="M${x} ${by-h} l${f1(Math.cos(a)*l)} ${f1(Math.sin(a)*l*.9)}" stroke="${['#F6DA86','#FFF4C8','#E8EDF4','#D9A940'][k%4]}" stroke-width="1.5" stroke-linecap="round"/>`}
   return s+pp+`<circle cx="${x}" cy="${by-h}" r="4" fill="#FFF4C8"/>`}
  function partyHorn(p,x1,y1){/* a rolled-up blowout lying on the table: mouthpiece, paper tube and its coil */
   let s=shadowAt(p,x1+40,y1+6,48,6);
   s+=`<rect x="${x1}" y="${y1-4}" width="20" height="8" rx="3" fill="url(#${p}d)" stroke="#7A5410" stroke-width=".8"/><rect x="${x1+18}" y="${y1-5}" width="6" height="10" rx="1" fill="#14244E"/>`;
   s+=`${[0,1,2,3,4,5,6].map(k=>`<path d="M${x1+21} ${y1+4} l${-4+k*1.5} ${10+(k%2)*3}" stroke="${k%2?'#F6DA86':'#E8EDF4'}" stroke-width="1.6" stroke-linecap="round"/>`).join('')}`;
   /* flat paper tube then a coil (seen from the side), red with a gold edge */
   const cx=x1+66,cyy=y1-6;let coil=`M${x1+24} ${y1} L${cx} ${y1}`;for(let k=0;k<=40;k++){const t=k/40,a=Math.PI/2+t*Math.PI*2*2.2,rr=14*(1-t*.62);coil+=`L${f1(cx+Math.cos(a)*rr)} ${f1(cyy+Math.sin(a)*rr*0+(-Math.sin(a+Math.PI/2))*0+Math.sin(a)*rr)}`}
   s+=`<path d="${coil}" stroke="#7A1424" stroke-width="7.5" fill="none" stroke-linejoin="round" stroke-linecap="round"/><path d="${coil}" stroke="#E84A5A" stroke-width="5" fill="none" stroke-linejoin="round" stroke-linecap="round"/><path d="${coil}" stroke="#FFB0B8" stroke-opacity=".6" stroke-width="1.2" fill="none" transform="translate(-1 -1.4)"/>`;
   return s}
  function curlRibbon(x,y,len,col,seed){const r=rnd(seed);let d=`M${x} ${y}`;for(let k=1;k<=60;k++){const t=k/60;const xx=x+t*len+Math.cos(t*Math.PI*8)*7,yy=y+Math.sin(t*Math.PI*8)*3.2+Math.sin(t*3)*4;d+=`L${f1(xx)} ${f1(yy)}`}
   return `<path d="${d}" stroke="#000" stroke-opacity=".35" stroke-width="3.6" fill="none" transform="translate(0 2)"/><path d="${d}" stroke="${col}" stroke-width="2.6" fill="none"/><path d="${d}" stroke="#fff" stroke-opacity=".5" stroke-width=".8" fill="none"/>`}
  function corkAndCage(p,x,y){/* the popped cork and its wire cage lying on the table */
   return shadowAt(p,x,y+4,16,4)+`<g transform="rotate(-70 ${x} ${y})"><path d="M${x-9} ${y-12} h18 v14 q-9 6 -18 0Z" fill="#C49A62"/><ellipse cx="${x}" cy="${y-12}" rx="11" ry="4.4" fill="#DDB98A" stroke="#8A6A3A" stroke-width="1"/><path d="M${x-9} ${y+2} q9 6 18 0 l-2 10 q-7 4 -14 0Z" fill="#B88A54"/><path d="M${x-6} ${y-8} h12 M${x-6} ${y-3} h12" stroke="#8A6A3A" stroke-width=".8"/></g>`+
    `<g transform="translate(${x+30} ${y+2})"><ellipse cx="0" cy="0" rx="10" ry="4" fill="none" stroke="#C9D2DE" stroke-width="1.6"/><path d="M-8 -1 L0 -14 L8 -1 M-3 2 L0 -14 L3 2" stroke="#C9D2DE" stroke-width="1.4" fill="none"/><ellipse cx="0" cy="-14" rx="5" ry="2" fill="url(#${p}d)"/></g>`}
  function tableScene(sc=1){const p='tb'+(RID++);let s=`<defs>${goldDefs(p)}${itemDefs(p)}<radialGradient id="${p}top" cx=".5" cy=".3" r=".8"><stop offset="0" stop-color="#2A2E44"/><stop offset="1" stop-color="#0E101C"/></radialGradient></defs>`;
   s+=`<ellipse class="roomglow" cx="0" cy="-420" rx="440" ry="320" fill="url(#${p}gl)"/>`;
   const XS=[-262,-172,-82,8],BY=-636,NS=116,WX=-165,WY=-300;
   s+=`<ellipse cx="0" cy="-6" rx="120" ry="16" fill="#000" opacity=".5"/><path d="M-100 -4 Q0 -40 100 -4 Q0 12 -100 -4Z" fill="#14151E" stroke="url(#${p}g)" stroke-width="3"/><path d="M-18 -20 C-30 -120 -10 -200 -22 -290 H22 C10 -200 30 -120 18 -20Z" fill="#171824"/><path d="M-8 -30 C-14 -120 -4 -200 -10 -288" stroke="#fff" stroke-opacity=".1" stroke-width="5" fill="none"/>`;
   s+=`<path d="M-262 -300 Q0 -248 262 -300 V-286 Q0 -232 -262 -286Z" fill="#0C0D16"/><path d="M-262 -286 Q0 -232 262 -286" stroke="url(#${p}g)" stroke-width="4" fill="none"/>`;
   s+=`<ellipse cx="0" cy="-300" rx="262" ry="52" fill="url(#${p}top)"/><ellipse cx="0" cy="-300" rx="262" ry="52" fill="none" stroke="url(#${p}g)" stroke-width="3"/>`;
   const r=rnd(5);const CC=['#E8B84A','#F4F7FB','#3A5AA8','#E84A5A','#C9D2DE'];const n=STATE==='day'?80:46;for(let k=0;k<n;k++){const a=r()*Math.PI*2,d=Math.sqrt(r());const x=Math.cos(a)*d*236,y=-300+Math.sin(a)*d*44;s+=k%3?`<rect x="${f1(x-3)}" y="${f1(y-1.4)}" width="6" height="2.8" fill="${CC[k%5]}" transform="rotate(${f1(r()*180)} ${f1(x)} ${f1(y)})"/>`:`<ellipse cx="${f1(x)}" cy="${f1(y)}" rx="2.6" ry="1.4" fill="${CC[k%5]}"/>`}
   s+=curlRibbon(-120,-322,70,'#E8B84A',3)+curlRibbon(40,-268,60,'#C9D2DE',4);
   s+=XS.map((x,k)=>`<path class="nstr" data-k="${k}" d="" stroke="#F4F0E6" stroke-width="1.6" fill="none"/>`).join('');
   s+=iceBucket(p,10,-318)+carriageClock(p,222,-318);
   s+=flute(p,104,-296,-3)+flute(p,152,-290,4);
   s+=tableHat(p,-226,-290);
   s+=shadowAt(p,WX,WY+20,18,4)+`<rect x="${WX-14}" y="${WY-2}" width="28" height="22" rx="3" fill="url(#${p}d)" stroke="#7A5410"/><rect x="${WX-3}" y="${WY-2}" width="6" height="22" fill="#22335C"/><path d="M${WX} ${WY-2} c-10 -10 -16 0 -6 2 Z M${WX} ${WY-2} c10 -10 16 0 6 2 Z" fill="#22335C"/><path d="M${WX-10} ${WY+2} V${WY+18}" stroke="#fff" stroke-opacity=".4" stroke-width="2"/>`;
   s+=partyHorn(p,-140,-266);
   if(STATE==='day'){s+=corkAndCage(p,-30,-262);
    /* streamers hanging off the front edge of the table after the pop */
    [[-200,'#E8B84A',70],[-120,'#C9D2DE',96],[60,'#3A5AA8',80],[150,'#E8B84A',60],[210,'#E84A5A',74]].forEach(([x,c,l],k)=>{const y0=-300+Math.sqrt(Math.max(0,1-(x/262)**2))*52+10;let d=`M${x} ${f1(y0)}`;for(let j=1;j<=20;j++){const t=j/20;d+=`L${f1(x+Math.sin(t*Math.PI*3+k)*6)} ${f1(y0+t*l)}`}s+=`<path class="stm" data-k="${k}" data-x="${x}" data-y="${f1(y0)}" d="${d}" stroke="${c}" stroke-width="5" fill="none" stroke-linecap="round"/>`})}
   s+=numberBalloons(p,XS,BY,NS);
   return `<g class="tscene" data-xs="${XS.join(',')}" data-by="${BY}" data-ns="${NS}" data-wx="${WX}" data-wy="${WY}" transform="scale(${sc})">${s}</g>`}

  // ---------- the whole room ----------
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1600, 150, 260, 340], table: [320, 880, 0.95], conf: 90 },
    tall: { w: 390, h: 844, win: null, table: [200, 800, 0.48], conf: 40 },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t), celebrate(t) } which poses everything at t seconds.
   * opts: { layout: "wide"|"tall", variant: "eve"|"day", year: 2027, align: "xMidYMax slice" }
   */
  function build(svg, opts) {
    opts = opts || {};
    const L = LAYOUTS[opts.layout || "wide"];
    const now = new Date();
    YEAR = opts.year || yearFor(now);
    STATE = opts.variant || variant(now);
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", opts.align || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win);
    svg.innerHTML = rm.bg + `<g transform="translate(${L.table[0]} ${L.table[1]})">${tableScene(L.table[2])}</g>` + rm.vig + confetti(L.w, L.h, L.conf);
    return pose(svg, STATE === "day");
  }

  /* gathers every moving part, and returns at(t) */
  function pose(root, day) {
    const q = (s) => [...root.querySelectorAll(s)];
    const bulbs = q(".bulbh"), glows = q(".roomglow"), secs = q(".secd");
    const bubs = q(".bub").map((g) => ({ x: +g.dataset.x, top: +g.dataset.top, bot: +g.dataset.bot, c: [...g.children] }));
    const scenes = q(".tscene").map((g) => ({ by: +g.dataset.by, ns: +g.dataset.ns, wx: +g.dataset.wx, wy: +g.dataset.wy, xs: g.dataset.xs.split(",").map(Number), nb: [...g.querySelectorAll(".numb")], st: [...g.querySelectorAll(".nstr")] }));
    const fws = q(".fw").map((g) => ({ cx: +g.dataset.cx, cy: +g.dataset.cy, R: +g.dataset.r, off: +g.dataset.off, gy: +g.dataset.gy, rk: g.querySelector(".rk"), fl: g.querySelector(".fl"), pts: [...g.querySelectorAll(".pt")].map((c) => ({ c, a: +c.dataset.a })) }));
    const snow = q(".sn").map((c) => { const H = +c.dataset.wh + 10; return { c, x: +c.dataset.x, y: +c.dataset.y, d: +c.dataset.d, H, n: Math.max(1, Math.round(+c.dataset.v * LOOP / H)) }; });
    const conf = root.querySelector(".conf");
    const cs = q(".conf rect").map((c) => { const H = +c.dataset.h + 40; return { c, x: +c.dataset.x, d: +c.dataset.d, H, n: Math.max(1, Math.round(+c.dataset.v * (+c.dataset.h) * 0.045 * LOOP / H)) }; });
    const walkers = q(".walkers").map((W) => ({ ww: +W.dataset.w, wh: +W.dataset.h, wk: [...W.querySelectorAll(".wk")] }));
    const mists = q(".ms"), streamers = q(".stm");
    if (conf) conf.setAttribute("opacity", day ? "1" : "0");
    // fireworks go off every few seconds, faster for a while right after midnight
    const FP = LOOP / 14;
    let fastFrom = -1;
    const bub = rq(0.45), mist = rq(0.35);
    function at(t) {
      bulbs.forEach((b) => { const k = +b.dataset.k; b.setAttribute("opacity", (0.75 + 0.25 * Math.sin(t * wq(1.6) + k * 1.9) * Math.sin(t * wq(0.7) + k)).toFixed(3)); });
      glows.forEach((g) => g.setAttribute("opacity", (0.9 + 0.1 * Math.sin(t * wq(1.3))).toFixed(3)));
      bubs.forEach((B) => B.c.forEach((c) => { const k = +c.dataset.k; const a = ((t * bub + k / 7) % 1); c.setAttribute("cx", f1(B.x + Math.sin(k * 2.3) * 6 + Math.sin(t * wq(3) + k) * 1)); c.setAttribute("cy", f1(B.bot - (B.bot - B.top) * a)); c.setAttribute("opacity", a > 0.92 ? "0" : "0.8"); }));
      secs.forEach((l) => l.setAttribute("transform", `rotate(${(Math.floor(t) * 6) % 360} ${l.dataset.x} ${l.dataset.y})`));
      scenes.forEach((S) => S.nb.forEach((g, k) => {
        const dx = Math.sin(t * wq(0.8) + k * 1.7) * 5, dy = Math.sin(t * wq(1.1) + k * 2.1) * 6, rot = Math.sin(t * wq(0.9) + k) * 3; const x = S.xs[k], y = S.by;
        g.setAttribute("transform", `translate(${f1(dx)} ${f1(dy)}) rotate(${f1(rot)} ${x} ${y})`);
        const tx = S.wx - 6 + k * 4, ty = S.wy; const bx = x + dx, by = y + S.ns * 0.1 + 6 + dy;
        S.st[k].setAttribute("d", `M${f1(bx)} ${f1(by)} C${f1(bx)} ${f1(by + 90)} ${f1(tx)} ${f1(ty - 120)} ${f1(tx)} ${f1(ty)}`);
      }));
      const fast = fastFrom >= 0 && t >= fastFrom && t < fastFrom + 14;
      const P = fast ? 1.5 : FP;
      fws.forEach((F) => {
        const a = ((t + F.off) % P) / P; const up = 0.18;
        if (a < up) { const e = a / up; F.rk.setAttribute("cx", f1(F.cx)); F.rk.setAttribute("cy", f1(F.gy - (F.gy - F.cy) * e)); F.rk.setAttribute("opacity", "1"); F.pts.forEach((p) => p.c.setAttribute("opacity", "0")); F.fl.setAttribute("opacity", "0"); }
        else {
          F.rk.setAttribute("opacity", "0"); const e = Math.min(1, (a - up) / 0.62); const rr = F.R * (1 - Math.pow(1 - e, 3));
          F.fl.setAttribute("cx", f1(F.cx)); F.fl.setAttribute("cy", f1(F.cy)); F.fl.setAttribute("opacity", e < 0.12 ? ((0.12 - e) * 2).toFixed(2) : "0");
          F.pts.forEach((p) => { p.c.setAttribute("cx", f1(F.cx + Math.cos(p.a) * rr)); p.c.setAttribute("cy", f1(F.cy + Math.sin(p.a) * rr + e * e * F.R * 0.45)); p.c.setAttribute("opacity", (e >= 1 ? 0 : 1 - e).toFixed(2)); });
        }
      });
      snow.forEach((S) => { const yy = ((S.y + t * S.n * S.H / LOOP) % S.H) - 5; S.c.setAttribute("cx", f1(S.x + Math.sin(t * wq(0.8) + S.d) * 6)); S.c.setAttribute("cy", f1(yy)); });
      if (day) cs.forEach((C) => { const y = (((C.d * 0.17) + t * C.n / LOOP) % 1) * C.H - 20; C.c.setAttribute("x", f1(C.x + Math.sin(t * wq(1.2) + C.d) * 14)); C.c.setAttribute("y", f1(y)); C.c.setAttribute("transform", `rotate(${f1((t * 360 * 15 / LOOP + C.d * 60) % 360)} ${f1(C.x)} ${f1(y)})`); });
      mists.forEach((m) => { const k = +m.dataset.k, x = +m.dataset.x, y = +m.dataset.y; const a = ((t * mist + k / 3) % 1); m.setAttribute("d", `M${f1(x + (k - 1) * 3)} ${f1(y - a * 40)} c${f1(6 * Math.sin(t * wq(1) + k))} -8 ${f1(-6 * Math.sin(t * wq(1.3) + k))} -16 ${f1(2 * Math.sin(t * wq(1) + k))} -24`); m.setAttribute("opacity", (Math.sin(a * Math.PI) * 0.35).toFixed(3)); });
      streamers.forEach((s) => { const k = +s.dataset.k; s.setAttribute("transform", `rotate(${f1(Math.sin(t * wq(1.1) + k * 1.3) * 4)} ${s.dataset.x} ${s.dataset.y})`); });
      walkers.forEach(({ ww, wh, wk }) => {
        const Pw = 30, v = (ww + 300) / 9;
        wk.forEach((g) => {
          const off = +g.dataset.off, sc = 1.5 * (+g.dataset.s); const tt = ((t + Pw - off) % Pw); const x = ww + 150 - tt * v;
          const y = groundY(x, ww, wh) + 2; const bob = Math.abs(Math.sin(tt * 7)) * 1.5;
          g.setAttribute("transform", `translate(${f1(x)} ${f1(y - bob)}) scale(${sc})`);
          g.querySelectorAll(".lg").forEach((l) => { const sw = Math.sin(tt * 7 + (+l.dataset.ph) * Math.PI) * 22; l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} ${l.dataset.p})`); });
          const bl = g.querySelector(".bls"); if (bl) bl.setAttribute("transform", `rotate(${f1(Math.sin(tt * 2) * 4)} -17 -62)`);
          g.querySelectorAll(".sp").forEach((p) => { const k = +p.dataset.k; const a = k / 12 * TAU + tt * 3 + Math.sin(tt * 20 + k) * 0.4; const l = 6 + Math.abs(Math.sin(tt * 17 + k * 1.3)) * 10; p.setAttribute("d", `M${f1(-23 + Math.cos(a) * 3)} ${f1(-106 + Math.sin(a) * 3)}L${f1(-23 + Math.cos(a) * l)} ${f1(-106 + Math.sin(a) * l)}`); });
          const h = g.querySelector(".horn");
          if (h) { const e = Math.max(0, Math.sin(tt * 2.2)); const L = 6 + e * 26; h.setAttribute("d", `M-14 -100 L${f1(-14 - L * 0.4)} -100`); g.querySelector(".horn2").setAttribute("d", e > 0.15 ? `M${f1(-14 - L * 0.4)} -100 L${f1(-14 - L)} ${f1(-100 - e * 2)} q-4 ${f1(-4 + e * 4)} -2 ${f1(-8 + e * 8)}` : `M${f1(-14 - L * 0.4)} -100 a4 4 0 1 0 0.1 0`); }
        });
      });
    }
    /** midnight: fireworks go off quickly for 14 seconds from t */
    function celebrate(t) { fastFrom = t; }
    return { at, celebrate };
  }

  /** Plays a built room live until the element leaves the page; scene.at may be swapped (svg.scene) */
  function play(svg, scene) {
    const start = performance.now() - 4000;
    const tick = (now) => { if (!svg.isConnected) return; scene.at((now - start) / 1000); requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
    return () => (performance.now() - start) / 1000;
  }

  G.BenflixNewYear = { LOOP, season, yearFor, variant, countdownSecs, greeting, countdown, build, play, LAYOUTS };
})(typeof window !== "undefined" ? window : globalThis);
