/* Benflix Valentine's Day: the Who's watching room (approved 2026-11-17, thread "Design a Valentine's Day theme for Benflix").
 *
 * A dim plum room lit by warm bulbs, with red paper hearts clipped along the wire between them that turn slowly.
 * Bottom left, a little round cafe table set for two: two tall red candles in brass candlesticks with flickering
 * flames, a glass vase of three red roses, two glasses of red wine, petals on the cloth and a heart-shaped box of
 * chocolates with its lid propped up. One chocolate goes each day from 1 February (13 in the box) until the last
 * one on the 13th and 14th. A window on the right shows a February evening with light snow: a couple holding hands,
 * someone carrying roses, a kid with a heart balloon and a dog walker with a trotting dog. On the 14th ("day") rose
 * petals drift down across the whole screen all day.
 *
 * Like feast.js and newyear.js, the same drawing is used live on the web and by tools/valentine/record.js, which
 * records it as looping videos for the TV and phone app. Everything is a function of the time t (seconds) and
 * repeats every LOOP seconds, so a video of one loop joins up with no jump.
 * The drawing code comes straight from the approved design sheet (valentines-2-whos-watching.html).
 */
(function (G) {
  "use strict";
  /** 30 seconds: each person outside walks past once per loop */
  const LOOP = 30;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;

  // ---------- dates ----------
  /** 1 to 14 February */
  const season = (d) => d.getMonth() === 1 && d.getDate() <= 14;
  /** "day" on the 14th (petals falling all day), else "season" */
  const variant = (d) => (d.getMonth() === 1 && d.getDate() === 14 ? "day" : "season");
  /** chocolates left in the box: 13 on 1 February, one fewer each day, the last one stays for the 13th and 14th */
  const chocolates = (d) => Math.max(1, 14 - Math.min(14, d.getDate()));
  /** the big words on Who's watching: the usual question, then "Happy Valentine's Day" on the 14th */
  const greeting = (d) => (variant(d) === "day" ? "Happy Valentine’s Day" : "Who’s watching?");
  /** the line under the greeting: "4 days to Valentine's Day", then "It's Valentine's Day" on the 14th */
  const countdown = (d) => {
    if (!season(d)) return "";
    const n = 14 - d.getDate();
    return n <= 0 ? "It’s Valentine’s Day" : `${n} day${n === 1 ? "" : "s"} to Valentine’s Day`;
  };
  /** the name of the recorded video for a day: "c13".."c1", or "day" on the 14th */
  const videoName = (d) => (variant(d) === "day" ? "day" : `c${chocolates(d)}`);

  // ---------- drawing kit (from the approved design sheet) ----------
  /** how many chocolates are drawn in the box (set by build) */
  let CHOCS = 13;
  let UID=0;const u=p=>p+(UID++)+'_';
  const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
  const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
  const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
  const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
  const f1=v=>(+v).toFixed(1);
  const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
  function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
  function smooth(P,closed){const n=P.length;let d=`M${f1(P[0][0])} ${f1(P[0][1])}`;const g=i=>closed?P[(i+n)%n]:P[Math.max(0,Math.min(n-1,i))];
   for(let i=0;i<(closed?n:n-1);i++){const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);d+=`C${f1(p1[0]+(p2[0]-p0[0])/6)} ${f1(p1[1]+(p2[1]-p0[1])/6)} ${f1(p2[0]-(p3[0]-p1[0])/6)} ${f1(p2[1]-(p3[1]-p1[1])/6)} ${f1(p2[0])} ${f1(p2[1])}`}return d+(closed?'Z':'')}
  function flameD(bx,by,w,h,t,ph,sp=1){const P=[];const N=7;
   for(let i=0;i<=N;i++){const s=i/N;const wob=Math.sin(t*wq(5.2)*sp+ph+s*3.1)*w*.16*s;const hw=w/2*Math.pow(1-s,.85)*(1+.25*Math.sin(s*Math.PI));P.push([bx-hw+wob,by-s*h])}
   const tipx=bx+Math.sin(t*wq(4.1)*sp+ph)*w*.2,tipy=by-h*(1.04+.08*Math.sin(t*wq(6.3)*sp+ph*2));
   const R=[];for(let i=N;i>=0;i--){const s=i/N;const wob=Math.sin(t*wq(5.2)*sp+ph+s*3.1)*w*.16*s;const hw=w/2*Math.pow(1-s,.85)*(1+.25*Math.sin(s*Math.PI));R.push([bx+hw+wob,by-s*h])}
   return smooth([...P.slice(0,N),[tipx,tipy],...R.slice(1)],true)}
  let RID=0;
  function groundY(x,ww,wh){return wh-40+Math.sin(x*.02)*3}
  /* ---------- the room: midnight-blue walls, warm string lights ---------- */
  const skin='#E8B48A',skin2='#B97E58';
  function leg(x,hipY,col,shoe,ph){return `<g class="lg" data-x="${x}" data-p="${hipY}" data-ph="${ph}"><path d="M${x} ${hipY} L${x} -3" stroke="${col}" stroke-width="6" stroke-linecap="round"/><path d="M${x+2} -1 h-8 a3 3 0 0 1 0 -5 h6Z" fill="${shoe}" stroke="${shoe}" stroke-width="2" stroke-linejoin="round"/></g>`}
  function face(x,y,r,hair,sk=skin){return `<circle cx="${x}" cy="${y}" r="${r}" fill="${sk}"/><circle cx="${x-r*.45}" cy="${y-r*.1}" r="${r*.13}" fill="#2A1A10"/><path d="M${x-r*.6} ${y+r*.35} q${r*.25} ${r*.2} ${r*.5} 0" stroke="#7A3A20" stroke-width="1.3" fill="none" stroke-linecap="round"/><circle cx="${x-r*.15}" cy="${y+r*.25}" r="${r*.2}" fill="#E07A6A" opacity=".45"/>`+(hair?`<path d="M${x-r} ${y-r*.1} C${x-r} ${y-r*1.3} ${x+r*1.1} ${y-r*1.4} ${x+r} ${y+r*.2} C${x+r*.6} ${y-r*.3} ${x} ${y-r*.6} ${x-r} ${y-r*.1}Z" fill="${hair}"/>`:'')}
  function person(x,o){/* a grown-up: coat, scarf, legs; o.hat draws on top */
   const H=o.h||1;let s=leg(x-5,-44*H,o.legs,'#15121A',0)+leg(x+5,-44*H,o.legs,'#15121A',1);
   s+=`<path d="M${x-14} ${-90*H} h28 l5 ${48*H} h-38Z" fill="${o.coat}"/><path d="M${x} ${-90*H} v${46*H}" stroke="#000" stroke-opacity=".18" stroke-width="2"/>`;
   s+=`<rect x="${x-12}" y="${-92*H}" width="24" height="7" rx="3.5" fill="${o.scarf}"/>`;
   s+=face(x,-102*H,11,o.hair,o.skin||skin)+(o.hat||'');return s}
  const HP='M50 90C22 70 4 52 4 31C4 15 16 4 30 4C39 4 46 9 50 17C54 9 61 4 70 4C84 4 96 15 96 31C96 52 78 70 50 90Z';
  const heartAt=(cx,cy,w,extra='',rot=0,sy=1)=>`<path d="${HP}" transform="translate(${f1(cx)} ${f1(cy)}) rotate(${rot}) scale(${f1(w/100)} ${f1(w/100*sy)}) translate(-50 -47)" ${extra}/>`;
  function room(W2,H2,win,lights=true){const id='rm'+(RID++);const fl=H2*.8;
   let s=`<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#140910"/><stop offset="1" stop-color="#26111C"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#22140F"/><stop offset="1" stop-color="#0A0606"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".6"/></radialGradient><radialGradient id="${id}h"><stop offset="0" stop-color="#FFD0A0" stop-opacity=".9"/><stop offset=".35" stop-color="#FFB27A" stop-opacity=".25"/><stop offset="1" stop-color="#FFB27A" stop-opacity="0"/></radialGradient><linearGradient id="${id}ph" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#F0566E"/><stop offset="1" stop-color="#A3122C"/></linearGradient></defs>`;
   s+=`<rect width="${W2}" height="${H2}" fill="url(#${id}w)"/>`;for(let x=0;x<W2;x+=W2/24)s+=`<rect x="${f1(x)}" y="0" width="${f1(W2/48)}" height="${fl}" fill="#fff" opacity=".014"/>`;
   s+=`<rect y="${f1(fl-H2*.16)}" width="${W2}" height="${f1(H2*.16)}" fill="#000" opacity=".2"/><rect y="${f1(fl-H2*.16)}" width="${W2}" height="4" fill="#4A2234" opacity=".8"/>`;
   if(win)s+=windowSVG(win);
   s+=`<rect y="${fl}" width="${W2}" height="${H2-fl}" fill="url(#${id}f)"/>`;for(let k=1;k<6;k++)s+=`<path d="M0 ${f1(fl+(H2-fl)*k/6)} H${W2}" stroke="#000" stroke-opacity=".25" stroke-width="2"/>`;
   s+=`<rect y="${fl-10}" width="${W2}" height="10" fill="#1A0B12"/>`;
   if(lights){const sc=W2/1920;const swags=W2<600?[[0,22,W2,22,22]]:[[300*sc,36*sc,800*sc,36*sc,96*sc],[800*sc,36*sc,1300*sc,36*sc,96*sc]];
    swags.forEach(([x1,y1,x2,y2,sag])=>{const N=Math.round((x2-x1)/(W2<600?34:48*sc));const pt=k=>{const t=k/N;return [x1+(x2-x1)*t,y1+(y2-y1)*t+sag*4*t*(1-t)]};
     let wire='';for(let k=0;k<=N;k++){const [x,y]=pt(k);wire+=(k?'L':'M')+f1(x)+' '+f1(y)}s+=`<path d="${wire}" stroke="#1A1014" stroke-width="${f1(3*Math.max(sc,.5))}" fill="none"/>`;
     for(let k=1;k<N;k++){const [x,y]=pt(k);const r=(W2<600?7:11*sc);
      if(k%2===0){/* a red paper heart clipped to the wire, turning gently */const hw=r*3;s+=`<path d="M${f1(x)} ${f1(y)}v${f1(r*.9)}" stroke="#E8E2D4" stroke-width="1"/><g class="phs" data-x="${f1(x)}" data-y="${f1(y+r*.9)}" data-k="${k}">${heartAt(x,y+r*.9+hw*.47,hw,`fill="url(#${id}ph)" stroke="#5E0B1C" stroke-width="3"`)}</g>`}
      else s+=`<rect x="${f1(x-r*.35)}" y="${f1(y)}" width="${f1(r*.7)}" height="${f1(r*.6)}" fill="#3A2A26"/><circle class="bulbh" data-k="${k}" cx="${f1(x)}" cy="${f1(y+r*1.6)}" r="${f1(r*3.2)}" fill="url(#${id}h)"/><circle cx="${f1(x)}" cy="${f1(y+r*1.6)}" r="${f1(r)}" fill="#FFE2C0"/><circle cx="${f1(x-r*.3)}" cy="${f1(y+r*1.3)}" r="${f1(r*.3)}" fill="#fff"/>`}})}
   return {bg:s,vig:`<rect width="${W2}" height="${H2}" fill="url(#${id}v)"/>`}}

  /* ---------- the window: a February evening street, light snow, people going out ---------- */
  function windowSVG([wx,wy,ww,wh]){const id='wn'+(RID++);const r=rnd(91);
   let s=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#140B26"/><stop offset=".65" stop-color="#3A1E44"/><stop offset="1" stop-color="#7A3A52"/></linearGradient><radialGradient id="${id}l"><stop offset="0" stop-color="#FFD98A" stop-opacity=".6"/><stop offset="1" stop-color="#FFD98A" stop-opacity="0"/></radialGradient></defs><rect width="${ww}" height="${wh}" fill="url(#${id}s)"/>`;
   for(let k=0;k<22;k++)s+=`<circle cx="${f1(r()*ww)}" cy="${f1(r()*wh*.35)}" r="${f1(.6+r()*.9)}" fill="#fff" opacity="${f1(.3+r()*.5)}"/>`;
   s+=`<circle cx="${f1(ww*.78)}" cy="${f1(wh*.16)}" r="${f1(ww*.06)}" fill="#FFF3DC"/><circle cx="${f1(ww*.8)}" cy="${f1(wh*.15)}" r="${f1(ww*.055)}" fill="url(#${id}s)" opacity=".0"/>`;
   [[.5,'#1E0E24',.55],[.62,'#12081A',.85]].forEach(([base,col,lit],row)=>{let x=-10;while(x<ww){const w=22+r()*34,h=wh*(.18+r()*.26)*(row?1:1.2),y=wh*base+wh*.25-h;s+=`<rect x="${f1(x)}" y="${f1(y)}" width="${f1(w)}" height="${f1(wh-y)}" fill="${col}"/>`;
    for(let yy=y+6;yy<wh-50;yy+=10)for(let xx=x+4;xx<x+w-6;xx+=8)if(r()<lit*.4)s+=`<rect x="${f1(xx)}" y="${f1(yy)}" width="3.4" height="4.6" fill="${r()<.15?'#FF8FA8':'#FFD98A'}" opacity="${f1(.35+r()*.55)}"/>`;x+=w+2+r()*6}});
   let g=`M0 ${f1(groundY(0,ww,wh))}`;for(let x=0;x<=ww;x+=10)g+=`L${x} ${f1(groundY(x,ww,wh))}`;g+=`V${wh} H0Z`;
   s+=`<path d="${g}" fill="#D8CCD8"/><path d="M0 ${f1(wh-22)} H${ww}" stroke="#A898B0" stroke-width="2" opacity=".6"/>`;
   const lx=ww*.86;s+=`<circle cx="${f1(lx)}" cy="${f1(wh-128)}" r="46" fill="url(#${id}l)"/><rect x="${f1(lx-2.5)}" y="${f1(wh-130)}" width="5" height="90" fill="#140A18"/><path d="M${f1(lx-9)} ${f1(wh-134)} h18 l-3 10 h-12Z" fill="#140A18"/><rect x="${f1(lx-6)}" y="${f1(wh-128)}" width="12" height="7" fill="#FFE2A0"/>`;
   let snow='';for(let k=0;k<22;k++)snow+=`<circle class="sn" data-x="${f1(r()*ww)}" data-y="${f1(r()*wh)}" data-v="${f1(8+r()*10)}" data-d="${f1(r()*6)}" data-wh="${wh}" r="${f1(1+r()*1.2)}" fill="#fff" opacity=".75"/>`;
   return `<rect x="${wx-14}" y="${wy-14}" width="${ww+28}" height="${wh+28}" rx="6" fill="#2A1420"/><svg x="${wx}" y="${wy}" width="${ww}" height="${wh}" overflow="hidden">${s}<g class="walkers" data-w="${ww}" data-h="${wh}">${walkers()}</g>${snow}</svg><rect x="${wx+ww/2-4}" y="${wy}" width="8" height="${wh}" fill="#2A1420"/><rect x="${wx}" y="${wy+wh/2-4}" width="${ww}" height="8" fill="#2A1420"/><rect x="${wx-22}" y="${wy+wh+12}" width="${ww+44}" height="10" rx="3" fill="#2A1420"/>`}

  /* ---------- people walking past (feet on the ground at y=0, facing left) ---------- */
  const beanie=(x,y,c,c2)=>`<path d="M${x-11} ${y+2} C${x-11} ${y-16} ${x+11} ${y-16} ${x+11} ${y+2}Z" fill="${c}"/><rect x="${x-12}" y="${y-1}" width="24" height="5" rx="2.5" fill="${c2}"/><circle cx="${x}" cy="${y-14}" r="4" fill="${c2}"/>`;
  function wCouple(){/* two people holding hands */
   let s=person(-16,{legs:'#2A2A3A',coat:'#8E2238',scarf:'#F4E6D8',hair:'#2A1A10',hat:beanie(-16,-110,'#F4E6D8','#C4142E')});
   s+=person(18,{h:1.06,legs:'#1E2440',coat:'#2E3A5A',scarf:'#C4142E',hair:'#5A3418',skin:skin2});
   s+=`<path d="M-8 -72 Q2 -60 10 -72" stroke="${skin}" stroke-width="5" fill="none" stroke-linecap="round"/>`;
   return `<g class="wk" data-off="0" data-s=".92">${s}</g>`}
  function wBouquet(){/* someone carrying a bunch of red roses wrapped in paper */
   let s=person(0,{legs:'#2A2230',coat:'#3A4A3A',scarf:'#E8B84A',hair:'#8A5A2A',hat:''});
   s+=`<path d="M-4 -84 L-15 -56" stroke="#2E3A2E" stroke-width="7" stroke-linecap="round"/>`;
   s+=`<g transform="translate(-8 22)">`;s+=`<path d="M-12 -64 L-30 -98 L-6 -104Z" fill="#F4E6D8" stroke="#C9B48F" stroke-width="1"/>`;
   [[-22,-104],[-14,-108],[-28,-110],[-18,-116],[-8,-104]].forEach(([x,y],k)=>{s+=`<circle cx="${x}" cy="${y}" r="5.4" fill="${k%2?'#C4142E':'#E2334F'}"/><path d="M${x-3} ${y-1}q3 -3 6 0" stroke="#7E0F24" stroke-width="1.2" fill="none"/>`});
   s+=`<path d="M-26 -100l-6 -4M-8 -100l5 -5" stroke="#3E8E4E" stroke-width="2.4" stroke-linecap="round"/></g><circle cx="-15" cy="-54" r="3.6" fill="${skin}"/>`;
   return `<g class="wk" data-off="8" data-s=".95">${s}</g>`}
  function wKid(){/* a kid holding a red heart balloon on a string */
   let s=leg(-4,-30,'#2E3A5A','#4A2A18',0)+leg(4,-30,'#2E3A5A','#4A2A18',1);
   s+=`<rect x="-11" y="-62" width="22" height="34" rx="9" fill="#E8A33A"/>`;
   s+=`<path d="M-4 -56 L-14 -74" stroke="#D08A2A" stroke-width="7" stroke-linecap="round"/><circle cx="-15" cy="-76" r="3.6" fill="${skin}"/>`;
   s+=`<g class="kbl"><path d="M-15 -76 Q-24 -110 -20 -150" stroke="#F4F0E6" stroke-width="1.1" fill="none"/>${heartAt(-20,-168,34,'fill="#E2334F" stroke="#8E0F24" stroke-width="3"')}<ellipse cx="-27" cy="-174" rx="3" ry="5" fill="#fff" opacity=".6" transform="rotate(30 -27 -174)"/></g>`;
   s+=face(0,-72,10,'#3A2210',skin2)+beanie(0,-78,'#4A6AB0','#fff');
   return `<g class="wk" data-off="15" data-s=".95">${s}</g>`}
  function dog(){/* a small dog trotting on a lead: four legs moving in diagonal pairs */
   const lg=(x,ph,c)=>`<g class="dl" data-x="${x}" data-ph="${ph}"><path d="M${x} -16 L${x} -2" stroke="${c}" stroke-width="4.5" stroke-linecap="round"/><ellipse cx="${x-1}" cy="-1.5" rx="3.2" ry="2" fill="#2A1A10"/></g>`;
   let s=lg(12,1,'#8A5A30')+lg(-12,0,'#8A5A30');
   s+=`<ellipse cx="0" cy="-20" rx="18" ry="9" fill="#B87A44"/><path class="tail" d="M16 -24 q8 -6 8 -14" stroke="#B87A44" stroke-width="4" fill="none" stroke-linecap="round"/>`;
   s+=`<circle cx="-18" cy="-28" r="8" fill="#B87A44"/><ellipse cx="-25" cy="-26" rx="5" ry="3.4" fill="#C98A54"/><circle cx="-29" cy="-27" r="1.8" fill="#1A0E08"/><circle cx="-19" cy="-31" r="1.4" fill="#1A0E08"/><path d="M-14 -34 q4 -2 3 8 q-4 -2 -3 -8Z" fill="#7A4A22"/><rect x="-16" y="-24" width="7" height="3" rx="1" fill="#C4142E"/>`;
   s+=lg(9,0,'#B87A44')+lg(-14,1,'#B87A44');
   return s}
  function wDog(){/* a dog walker in a long coat, with a heart on the dog's red jumper */
   let s=person(0,{legs:'#1C1C26',coat:'#5A2E4A',scarf:'#F7A3BA',hair:'#C8A060',hat:''});
   s+=`<path d="M-6 -84 L-14 -66" stroke="#4A2440" stroke-width="7" stroke-linecap="round"/><circle cx="-15" cy="-64" r="3.6" fill="${skin}"/>`;
   s+=`<path class="lead" d="M-15 -64 Q-30 -40 -44 -26" stroke="#C4142E" stroke-width="1.6" fill="none"/><g transform="translate(-30 0)"><g class="dogb">${dog()}</g></g>`;
   return `<g class="wk" data-off="22" data-s=".92">${s}</g>`}
  function walkers(){return wCouple()+wBouquet()+wKid()+wDog()}
  /* ---------- the table set for two ---------- */
  function defs2(p){return `<linearGradient id="${p}br" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#7A5414"/><stop offset=".35" stop-color="#F2D27A"/><stop offset=".6" stop-color="#C9962E"/><stop offset="1" stop-color="#5A3C0C"/></linearGradient>
   <linearGradient id="${p}cd" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#7E0F24"/><stop offset=".4" stop-color="#E2334F"/><stop offset="1" stop-color="#8E0F24"/></linearGradient>
   <linearGradient id="${p}cl" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F8EEE2"/><stop offset="1" stop-color="#CDB9A6"/></linearGradient>
   <linearGradient id="${p}cl2" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#A8907E"/><stop offset=".3" stop-color="#F4E8DA"/><stop offset=".7" stop-color="#E8D8C6"/><stop offset="1" stop-color="#9A826E"/></linearGradient>
   <linearGradient id="${p}gl" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity=".28"/><stop offset=".3" stop-color="#fff" stop-opacity=".06"/><stop offset=".8" stop-color="#fff" stop-opacity=".1"/><stop offset="1" stop-color="#fff" stop-opacity=".3"/></linearGradient>
   <linearGradient id="${p}wn" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#4A0614"/><stop offset=".5" stop-color="#9A1430"/><stop offset="1" stop-color="#4A0614"/></linearGradient>
   <radialGradient id="${p}rs" cx=".4" cy=".35" r=".7"><stop offset="0" stop-color="#FF5A70"/><stop offset=".6" stop-color="#C4142E"/><stop offset="1" stop-color="#6A0818"/></radialGradient>
   <linearGradient id="${p}bx" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#F0566E"/><stop offset=".5" stop-color="#C4142E"/><stop offset="1" stop-color="#7E0F24"/></linearGradient>
   <radialGradient id="${p}ch" cx=".38" cy=".32" r=".7"><stop offset="0" stop-color="#9A5A3C"/><stop offset=".6" stop-color="#5A2E1C"/><stop offset="1" stop-color="#2A140A"/></radialGradient>
   <radialGradient id="${p}glow"><stop offset="0" stop-color="#FFC27A" stop-opacity=".55"/><stop offset=".4" stop-color="#FF9A5A" stop-opacity=".16"/><stop offset="1" stop-color="#FF9A5A" stop-opacity="0"/></radialGradient>`}
  /* a tall red candle in a brass candlestick; the flame is drawn each frame */
  function candle(p,x,y,h,k){let s=`<ellipse cx="${x}" cy="${y}" rx="26" ry="7" fill="url(#${p}br)" stroke="#5A3C0C" stroke-width="1"/><path d="M${x-22} ${y-2} q22 -10 44 0 l-2 -6 q-20 -8 -40 0Z" fill="url(#${p}br)"/>
   <path d="M${x-5} ${y-8} C${x-9} ${y-30} ${x-3} ${y-44} ${x-7} ${y-62} h14 C${x+3} ${y-44} ${x+9} ${y-30} ${x+5} ${y-8}Z" fill="url(#${p}br)"/><ellipse cx="${x}" cy="${y-36}" rx="7" ry="3.4" fill="url(#${p}br)" stroke="#5A3C0C" stroke-width=".8"/>
   <path d="M${x-14} ${y-62} q14 6 28 0 l-3 -10 h-22Z" fill="url(#${p}br)" stroke="#5A3C0C" stroke-width=".8"/><ellipse cx="${x}" cy="${y-72}" rx="11" ry="3.4" fill="#7A5414"/>`;
   const top=y-72-h;s+=`<rect x="${x-7}" y="${top}" width="14" height="${h}" fill="url(#${p}cd)"/><ellipse cx="${x}" cy="${top}" rx="7" ry="2.4" fill="#F0566E"/>`;
   /* one wax drip running down the side */
   s+=`<path d="M${x+3} ${top} q3 0 3 ${f1(h*.18)} q0 6 -2.5 6 q-2 0 -2 -6Z" fill="#E2334F"/><path d="M${x-4} ${top+4} v${f1(h*.8)}" stroke="#fff" stroke-width="1.6" opacity=".25"/>`;
   s+=`<path d="M${x} ${top} v-7" stroke="#2A1A10" stroke-width="1.4"/><circle class="cglow" data-k="${k}" cx="${x}" cy="${top-22}" r="70" fill="url(#${p}glow)"/><path class="cfl" data-x="${x}" data-y="${top-5}" data-k="${k}" d="" fill="#FFB34A"/><path class="cfl2" data-x="${x}" data-y="${top-5}" data-k="${k}" d="" fill="#FFF4C8"/>`;
   return s}
  /* one rose head seen a little from the side: cupped outer petals, tight swirl in the middle */
  function rose(p,x,y,s,rot=0){let o=`<g transform="translate(${x} ${y}) rotate(${rot}) scale(${s})">`;
   o+=`<path d="M-14 6 C-20 -2 -16 -12 -8 -12 C-2 -18 8 -16 12 -10 C20 -8 20 4 13 8 C6 14 -8 14 -14 6Z" fill="#7E0F24"/>`;
   o+=`<path d="M-15 2 C-17 -8 -8 -14 -2 -10 C-4 -2 -8 4 -15 2Z" fill="url(#${p}rs)" stroke="#5A0716" stroke-width=".6"/><path d="M15 2 C17 -8 8 -15 0 -11 C3 -3 8 4 15 2Z" fill="url(#${p}rs)" stroke="#5A0716" stroke-width=".6"/>`;
   o+=`<path d="M-13 3 C-10 12 10 12 13 3 C8 8 -6 9 -13 3Z" fill="#A3122C"/><path d="M-10 -2 C-8 -12 8 -13 10 -2 C6 4 -6 4 -10 -2Z" fill="#E2334F"/>`;
   o+=`<path d="M-6 -4 C-6 -10 5 -11 6 -5 C4 -1 -3 -1 -6 -4Z" fill="#C4142E"/><path d="M-4 -6 C-1 -10 5 -8 3 -4 M-2 -6 c1 -2 4 -1 3 1" stroke="#6A0818" stroke-width="1" fill="none"/><path d="M-9 -1 C-7 3 3 4 8 -1" stroke="#FF8090" stroke-width=".9" fill="none" opacity=".6"/>`;
   o+=`<path d="M-6 9 l-5 6 l7 -3 M6 9 l5 6 l-7 -3" fill="#2E6A3A"/></g>`;return o}
  function vase(p,x,y){let s='';/* stems first, visible through the glass */
   const heads=[[x-30,y-250,-18],[x+4,y-282,4],[x+34,y-244,20]];
   heads.forEach(([hx,hy],k)=>{s+=`<path d="M${x-4+k*4} ${y-8} C${x-4+k*4} ${y-90} ${hx} ${hy+90} ${hx} ${hy+10}" stroke="#2E6A3A" stroke-width="3.4" fill="none"/>`});
   s+=`<path d="M${x-12} ${y-170} q-26 -8 -30 -30 q22 4 30 30Z M${x+10} ${y-150} q26 -12 32 -34 q-24 6 -32 34Z" fill="#3E8E4E" stroke="#1E4A2A" stroke-width="1"/><path d="M${x-12} ${y-170} l-22 -24 M${x+10} ${y-150} l24 -28" stroke="#1E4A2A" stroke-width="1"/>`;
   s+=`<path d="M${x-26} ${y-6} C${x-36} ${y-50} ${x-30} ${y-90} ${x-12} ${y-118} V${y-150} h24 V${y-118} C${x+30} ${y-90} ${x+36} ${y-50} ${x+26} ${y-6}Z" fill="#9FC4C0" fill-opacity=".14" stroke="#DCEBEA" stroke-opacity=".55" stroke-width="2"/>`;
   s+=`<path d="M${x-30} ${y-60} C${x-34} ${y-40} ${x-32} ${y-20} ${x-26} ${y-6} H${x+26} C${x+32} ${y-20} ${x+34} ${y-40} ${x+30} ${y-60}Z" fill="#8FC0B0" fill-opacity=".2"/><ellipse cx="${x}" cy="${y-60}" rx="30" ry="4" fill="#DCEBEA" opacity=".3"/>`;
   s+=`<ellipse cx="${x}" cy="${y-150}" rx="12" ry="3" fill="none" stroke="#DCEBEA" stroke-opacity=".6" stroke-width="2"/><path d="M${x-20} ${y-20} C${x-28} ${y-50} ${x-22} ${y-86} ${x-9} ${y-112}" stroke="#fff" stroke-opacity=".45" stroke-width="3" fill="none" stroke-linecap="round"/><ellipse cx="${x}" cy="${y-5}" rx="26" ry="5" fill="#DCEBEA" opacity=".25"/>`;
   heads.forEach(([hx,hy,r])=>{s+=rose(p,hx,hy,1.55,r)});return s}
  /* a wine glass with red wine in it, light catching the rim */
  function wineGlass(p,x,y,lean=0){return `<g transform="rotate(${lean} ${x} ${y})"><ellipse cx="${x}" cy="${y}" rx="22" ry="5" fill="#DCEBEA" fill-opacity=".25" stroke="#DCEBEA" stroke-opacity=".5"/><path d="M${x-1.6} ${y-2} V${y-56} h3.2 V${y-2}" fill="#DCEBEA" fill-opacity=".45"/>
   <path d="M${x-22} ${y-112} C${x-26} ${y-80} ${x-14} ${y-58} ${x} ${y-56} C${x+14} ${y-58} ${x+26} ${y-80} ${x+22} ${y-112}" fill="#DCEBEA" fill-opacity=".1" stroke="#EAF2F2" stroke-opacity=".55" stroke-width="1.6"/>
   <path d="M${x-23} ${y-88} C${x-22} ${y-70} ${x-12} ${y-58} ${x} ${y-57} C${x+12} ${y-58} ${x+22} ${y-70} ${x+23} ${y-88}Z" fill="url(#${p}wn)"/><ellipse cx="${x}" cy="${y-88}" rx="23" ry="4.4" fill="#B81C3C"/><ellipse cx="${x-6}" cy="${y-89}" rx="8" ry="1.4" fill="#FF8DA6" opacity=".5"/>
   <ellipse cx="${x}" cy="${y-112}" rx="22" ry="4.6" fill="none" stroke="#fff" stroke-opacity=".55" stroke-width="1.4"/><path d="M${x-16} ${y-104} C${x-19} ${y-86} ${x-14} ${y-72} ${x-7} ${y-64}" stroke="#fff" stroke-opacity=".55" stroke-width="2.6" fill="none" stroke-linecap="round"/></g>`}
  /* heart-shaped box of chocolates, lid propped behind it; one chocolate goes each day */
  const CUPS=[[-36,-10],[-18,-14],[18,-14],[36,-10],[-44,0],[-26,-2],[-8,-3],[8,-3],[26,-2],[44,0],[-24,10],[0,9],[24,10]].map(([x,y])=>[x*1.3,y*1.3]);
  function chocBox(p,x,y){let s='';const W=184;
   /* lid: an upright heart of red satin with a ribbon and bow, leaning on the vase behind */
   s+=`<g transform="rotate(-8 ${x+30} ${y-20})">${heartAt(x+30,y-96,W*.92,`fill="url(#${p}bx)" stroke="#5E0B1C" stroke-width="1.5"`)}${heartAt(x+30,y-96,W*.8,`fill="none" stroke="#FF8DA6" stroke-width="2.2" opacity=".55"`)}
   <path d="M${x-28} ${y-112} L${x+86} ${y-58}" stroke="#F4E6D8" stroke-width="7"/><path d="M${x+20} ${y-92} c-20 -22 -40 -6 -18 6 c-24 10 -6 26 18 -6Z M${x+20} ${y-92} c22 -18 40 2 18 10 c20 14 0 26 -18 -10Z" fill="#F4E6D8" stroke="#C9B48F" stroke-width="1.2"/><circle cx="${x+20}" cy="${y-92}" r="6" fill="#E8DCC8" stroke="#C9B48F"/>
   <path d="M${x-8} ${y-122} q-8 20 0 36" stroke="#fff" stroke-width="4" opacity=".3" fill="none" stroke-linecap="round"/></g>`;
   /* box: a squashed heart (seen from above) with a red side wall */
   s+=`<g>${heartAt(x,y+6,W,`fill="#5E0B1C"`,0,.42)}${heartAt(x,y,W,`fill="url(#${p}bx)"`,0,.42)}${heartAt(x,y-1,W*.9,`fill="#2A0A12"`,0,.4)}`;
   const n=CHOCS;CUPS.forEach(([cx,cy],k)=>{const X=x+cx,Y=y+cy;s+=`<ellipse cx="${X}" cy="${Y}" rx="9.5" ry="5.6" fill="#C9962E" stroke="#7A5414" stroke-width=".8"/><ellipse cx="${X}" cy="${Y-.6}" rx="7.4" ry="4" fill="#5A3C0C"/>`;
    if(k<n){const heart=k%3===1;s+=heart?heartAt(X,Y-4,15,`fill="url(#${p}ch)" stroke="#1A0C06" stroke-width="5"`,0,.75):`<ellipse cx="${X}" cy="${Y-3}" rx="7.4" ry="5.6" fill="url(#${p}ch)"/>`;s+=k%4===0?`<path d="M${X-4} ${Y-4} q2 -2 4 0 q2 -2 4 0" stroke="#F4E6D8" stroke-width="1.2" fill="none"/>`:k%4===2?`<ellipse cx="${X}" cy="${Y-4}" rx="3" ry="1.6" fill="#E2334F"/>`:`<circle cx="${X-2.4}" cy="${Y-5.4}" r="1.4" fill="#fff" opacity=".5"/>`}});
   return s+`</g>`}
  function petalAt(x,y,r,rot,c){return `<path d="M${f1(x)} ${f1(y-r)} C${f1(x+r*1.1)} ${f1(y-r*.6)} ${f1(x+r*.8)} ${f1(y+r*.8)} ${f1(x)} ${f1(y+r*.6)} C${f1(x-r*.8)} ${f1(y+r*.8)} ${f1(x-r*1.1)} ${f1(y-r*.6)} ${f1(x)} ${f1(y-r)}Z" fill="${c}" transform="rotate(${f1(rot)} ${f1(x)} ${f1(y)}) scale(1 .6) translate(0 ${f1(y*(1/.6-1))})"/>`}
  function tableScene(sc=1){const p='tb'+(RID++);let s=`<defs>${defs2(p)}</defs>`;
   s+=`<ellipse class="roomglow" cx="-40" cy="-420" rx="440" ry="320" fill="url(#${p}glow)"/>`;
   /* round cafe table: dark wood pedestal, cream cloth that drapes in soft folds */
   s+=`<ellipse cx="0" cy="-6" rx="120" ry="16" fill="#000" opacity=".5"/><path d="M-90 -4 Q0 -34 90 -4 Q0 10 -90 -4Z" fill="#2A160E"/><path d="M-14 -20 C-20 -80 -8 -140 -14 -200 H14 C8 -140 20 -80 14 -20Z" fill="#3A2014"/><path d="M-6 -30 C-10 -80 -2 -140 -6 -196" stroke="#fff" stroke-opacity=".08" stroke-width="4" fill="none"/>`;
   let hem=`M-252 -300`;for(let k=0;k<=12;k++){const x=-252+k*42;hem+=`L${x} ${k%2?-176:-188}`;}
   s+=`<path d="M-252 -300 Q0 -246 252 -300 L258 -188 ${(()=>{let d='';for(let k=12;k>=0;k--){const x=-258+k*43;d+=`Q${f1(x+21)} ${k%2?-170:-180} ${f1(x)} ${k%2?-186:-176} `}return d})()}Z" fill="url(#${p}cl2)"/>`;
   for(let k=1;k<12;k++){const x=-252+k*42;s+=`<path d="M${f1(x*.96)} ${f1(-262+Math.abs(x)*.02)} Q${f1(x*1.01)} -220 ${f1(x*1.02)} -180" stroke="#8A7260" stroke-opacity=".35" stroke-width="3" fill="none"/>`}
   s+=`<ellipse cx="0" cy="-300" rx="256" ry="52" fill="url(#${p}cl)"/><ellipse cx="0" cy="-300" rx="256" ry="52" fill="none" stroke="#B8A08C" stroke-opacity=".5" stroke-width="2"/>`;
   /* a red runner across the middle of the cloth */
   s+=`<path d="M-120 -346 Q0 -352 120 -346 L150 -252 Q0 -244 -150 -252Z" fill="#8E1A34" opacity=".9"/><path d="M-150 -252 Q0 -244 150 -252 L152 -226 Q0 -218 -152 -226Z" fill="#6E1428"/><path d="M-116 -340 Q0 -346 116 -340 M-146 -258 Q0 -250 146 -258" stroke="#E8B84A" stroke-width="1.6" fill="none" opacity=".7"/>`;
   /* petals already lying on the cloth */
   const r=rnd(8);for(let k=0;k<12;k++){const a=r()*Math.PI*2,d=.35+r()*.6;s+=petalAt(Math.cos(a)*d*220,-300+Math.sin(a)*d*40,7,r()*180,k%2?'#C4142E':'#E2334F')}
   /* back row: vase of roses, candles; front row: glasses and the chocolates */
   s+=vase(p,-50,-322);
   s+=candle(p,-196,-312,150,0)+candle(p,-142,-302,110,1);
   s+=chocBox(p,170,-296);
   s+=wineGlass(p,40,-274,-2)+wineGlass(p,-100,-262,3);
   return `<g class="tscene" transform="scale(${sc})">${s}</g>`}
  function petals(W,H,n){const r=rnd(23);const CC=['#E2334F','#C4142E','#F7A3BA','#A3122C'];return `<g class="pet" opacity="0">${Array.from({length:n},(_,k)=>`<path data-x="${f1(r()*W)}" data-v="${f1(.4+r()*.5)}" data-d="${f1(r()*6)}" data-h="${H}" data-s="${f1(Math.max(W/180,7)*(.8+r()*.5))}" d="" fill="${CC[k%4]}"/>`).join('')}</g>`}

  // ---------- the whole room ----------
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1590, 140, 280, 350], table: [296, 868, 1], petals: 70 },
    tall: { w: 390, h: 844, win: null, table: [200, 806, 0.5], petals: 30 },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t) } which poses everything at t seconds.
   * opts: { layout: "wide"|"tall", variant: "season"|"day", chocolates: 0..13, align: "xMidYMax slice" }
   */
  function build(svg, opts) {
    opts = opts || {};
    const L = LAYOUTS[opts.layout || "wide"];
    const now = new Date();
    const v = opts.variant || variant(now);
    CHOCS = opts.chocolates != null ? opts.chocolates : chocolates(now);
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", opts.align || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win);
    svg.innerHTML = rm.bg + `<g transform="translate(${L.table[0]} ${L.table[1]})">${tableScene(L.table[2])}</g>` + rm.vig + petals(L.w, L.h, L.petals);
    return pose(svg, v === "day");
  }

  /* gathers every moving part, and returns at(t) */
  function pose(root, day) {
    const q = (s) => [...root.querySelectorAll(s)];
    const bulbs = q(".bulbh"), hearts = q(".phs"), glows = q(".roomglow"), cglows = q(".cglow");
    const flames = q(".cfl"), flames2 = q(".cfl2");
    const snow = q(".sn").map((c) => { const H = +c.dataset.wh + 10; return { c, x: +c.dataset.x, y: +c.dataset.y, d: +c.dataset.d, H, n: Math.max(1, Math.round(+c.dataset.v * LOOP / H)) }; });
    const pet = root.querySelector(".pet");
    const ps = q(".pet path").map((c) => { const H = +c.dataset.h + 40; return { c, x: +c.dataset.x, d: +c.dataset.d, s: +c.dataset.s, H, n: Math.max(1, Math.round(+c.dataset.v * (+c.dataset.h) * 0.04 * LOOP / H)) }; });
    const walkers = q(".walkers").map((W) => ({ ww: +W.dataset.w, wh: +W.dataset.h, wk: [...W.querySelectorAll(".wk")] }));
    if (pet) pet.setAttribute("opacity", day ? "1" : "0");
    // each petal's shape is set once; only where it is changes
    ps.forEach((P) => { const sz = P.s; P.c.setAttribute("d", `M0 ${-sz} C${sz * 1.1} ${-sz * 0.6} ${sz * 0.8} ${sz * 0.8} 0 ${sz * 0.6} C${-sz * 0.8} ${sz * 0.8} ${-sz * 1.1} ${-sz * 0.6} 0 ${-sz}Z`); });
    const spin = Math.round(50 * LOOP / 360) * 360 / LOOP;
    function at(t) {
      bulbs.forEach((b) => { const k = +b.dataset.k; b.setAttribute("opacity", (0.75 + 0.25 * Math.sin(t * wq(1.6) + k * 1.9) * Math.sin(t * wq(0.7) + k)).toFixed(3)); });
      hearts.forEach((g) => { const k = +g.dataset.k; g.setAttribute("transform", `rotate(${f1(Math.sin(t * wq(1.1) + k) * 7)} ${g.dataset.x} ${g.dataset.y})`); });
      glows.forEach((g) => g.setAttribute("opacity", (0.88 + 0.08 * Math.sin(t * wq(7.3)) + 0.04 * Math.sin(t * wq(13.1))).toFixed(3)));
      flames.forEach((f) => { const k = +f.dataset.k; f.setAttribute("d", flameD(+f.dataset.x, +f.dataset.y, 11, 30, t, k * 2.1, 1)); });
      flames2.forEach((f) => { const k = +f.dataset.k; f.setAttribute("d", flameD(+f.dataset.x, +f.dataset.y - 1, 5.4, 15, t, k * 2.1, 1)); });
      cglows.forEach((g) => { const k = +g.dataset.k; g.setAttribute("opacity", (0.85 + 0.15 * Math.sin(t * wq(9) + k * 2) * Math.sin(t * wq(3.3) + k)).toFixed(3)); });
      snow.forEach((S) => { const yy = ((S.y + t * S.n * S.H / LOOP) % S.H) - 5; S.c.setAttribute("cx", f1(S.x + Math.sin(t * wq(0.8) + S.d) * 6)); S.c.setAttribute("cy", f1(yy)); });
      if (day) ps.forEach((P) => {
        const y = (((P.d * 0.17) + t * P.n / LOOP) % 1) * P.H - 20; const x = P.x + Math.sin(t * wq(0.9) + P.d) * 24;
        const rot = (t * spin + P.d * 60) % 360; const fl = Math.cos(t * wq(1.7) + P.d);
        P.c.setAttribute("transform", `translate(${f1(x)} ${f1(y)}) rotate(${f1(rot)}) scale(${f1(Math.max(0.15, Math.abs(fl)))} 1)`);
      });
      walkers.forEach(({ ww, wh, wk }) => {
        const Pw = LOOP, v = (ww + 300) / 9;
        wk.forEach((g) => {
          const off = +g.dataset.off, sc = 1.5 * (+g.dataset.s); const tt = ((t + Pw - off) % Pw); const x = ww + 150 - tt * v;
          const y = groundY(x, ww, wh) + 2; const bob = Math.abs(Math.sin(tt * 7)) * 1.5;
          g.setAttribute("transform", `translate(${f1(x)} ${f1(y - bob)}) scale(${sc})`);
          g.querySelectorAll(".lg").forEach((l) => { const sw = Math.sin(tt * 7 + (+l.dataset.ph) * Math.PI) * 22; l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} ${l.dataset.p})`); });
          g.querySelectorAll(".dl").forEach((l) => { const sw = Math.sin(tt * 14 + (+l.dataset.ph) * Math.PI) * 28; l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} -16)`); });
          const db = g.querySelector(".dogb"); if (db) db.setAttribute("transform", `translate(0 ${f1(-Math.abs(Math.sin(tt * 14)) * 1.6 + bob)})`);
          const tl = g.querySelector(".tail"); if (tl) tl.setAttribute("d", `M16 -24 q${f1(8 + Math.sin(tt * 18) * 4)} -6 ${f1(6 + Math.sin(tt * 18) * 6)} -14`);
          const kb = g.querySelector(".kbl"); if (kb) kb.setAttribute("transform", `rotate(${f1(Math.sin(tt * 2) * 5)} -15 -76)`);
        });
      });
    }
    return { at };
  }

  /** Plays a built room live until the element leaves the page; scene.at may be swapped (svg.scene) */
  function play(svg, scene) {
    const start = performance.now() - 4000;
    const tick = (now) => { if (!svg.isConnected) return; scene.at((now - start) / 1000); requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
    return () => (performance.now() - start) / 1000;
  }

  G.BenflixValentine = { LOOP, season, variant, chocolates, greeting, countdown, videoName, build, play, LAYOUTS };
})(typeof window !== "undefined" ? window : globalThis);
