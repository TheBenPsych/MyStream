/* Benflix Video Games Day: the Who's watching arcade room (approved 2026-11-17, thread "Design a Video Games Day theme for Benflix").
 *
 * A dim purple arcade room with a neon carpet and a pink and cyan "GAME ON" sign whose last letter flickers.
 * Bottom left, two made-up upright arcade machines stand side by side: Comet Run (a little ship dodging and
 * zapping pixel comets) and Pixel Rally (a pixel car racing down a road towards a sunset), both playing their
 * demo, with chasing marquee bulbs, glowing coin slots and a red stool. Through the window on the right it's a
 * September night with an arcade across the street, and every 30 seconds a little parade of our own game-style
 * characters goes past (a robot frog animatronic, an armoured space soldier, a pixel car, a runner in a star
 * cap and a hooded adventurer). Everything is original: no real games, machines or characters. 12 September only.
 *
 * Like feast.js, the same drawing is used live on the web and by tools/arcade/record.js, which records it as
 * looping videos for the TV and phone app. Everything is a function of the time t (seconds) and repeats
 * every LOOP seconds, so a video of one loop joins up with no jump.
 * The drawing code comes straight from the approved design sheet (videogames-2-arcade-room.html).
 */
(function (G) {
  "use strict";
  const LOOP = 30;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;

  // ---------- dates ----------
  /** 12 September only */
  const season = (d) => d.getMonth() === 8 && d.getDate() === 12;
  /** "Happy Video Games Day", in pixel capitals */
  const greeting = () => "Happy Video Games Day";
  /** the line under the greeting */
  const LINE = "Player 1 ready";

  // ---------- drawing kit (from the approved design sheet) ----------


  let UID=0;const u=p=>p+(UID++)+'_';
  const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
  const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
  const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
  const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
  const f1=v=>(+v).toFixed(1);
  const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
  const GDEF=`<defs><filter id="Gts" x="-10%" y="-40%" width="120%" height="180%"><feDropShadow dx="0" dy="2" stdDeviation="2.5" flood-color="#000" flood-opacity=".55"/></filter><filter id="Gglow" x="-20%" y="-60%" width="140%" height="220%"><feDropShadow dx="0" dy="0" stdDeviation="10" flood-color="#000" flood-opacity=".85"/></filter></defs>`;
  const T=(x,y,s,txt,o={})=>`<text x="${x}" y="${y}" font-family="${o.font||'Outfit,sans-serif'}" font-weight="${o.w||600}" font-size="${s}" fill="${o.fill||'#F5F0EA'}" ${o.anchor?`text-anchor="${o.anchor}"`:''} ${o.sh?'filter="url(#Gts)"':''} ${o.op?`opacity="${o.op}"`:''} ${o.cls?`class="${o.cls}"`:''}>${txt}</text>`;
  function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
  const CY='#3FE0FF',PK='#FF4FA8',YL='#FFD23F',LM='#9CFF57',PU='#4A2A86';
  let RID=0;

  /* ---------- the room: dark purple walls, arcade carpet ---------- */
  function room(W,H,win){const id='rm'+(RID++);const fl=H*.8;const sc=W/1920;
   let s=`<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0B0716"/><stop offset="1" stop-color="#1A0F30"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#120B22"/><stop offset="1" stop-color="#07050E"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".6"/></radialGradient></defs>`;
   s+=`<rect width="${W}" height="${H}" fill="url(#${id}w)"/>`;for(let x=0;x<W;x+=W/16)s+=`<rect x="${f1(x)}" y="0" width="${f1(W/32)}" height="${fl}" fill="#fff" opacity=".012"/>`;
   /* a thin neon trim along the wall, like an arcade */
   s+=`<rect y="${f1(fl-H*.14)}" width="${W}" height="${f1(H*.14)}" fill="#000" opacity=".25"/><rect y="${f1(fl-H*.14)}" width="${W}" height="${f1(3*Math.max(sc,.5))}" fill="${CY}" opacity=".35"/>`;
   if(win)s+=windowSVG(win);
   s+=`<rect y="${fl}" width="${W}" height="${H-fl}" fill="url(#${id}f)"/>`;
   /* arcade carpet: little neon shapes scattered on dark, smaller and dimmer further back */
   const r=rnd(5);const CC=[CY,PK,YL,LM,'#B69CFF'];
   for(let k=0;k<Math.round(150*Math.max(sc,.4));k++){const t=r(),y=fl+8+(H-fl-8)*t*t,x=r()*W,z=(.35+t*.9)*Math.max(sc,.45),c=CC[k%5],op=f1(.16+t*.3),ty=Math.floor(r()*4),rot=f1(r()*360);
    const g=ty===0?`<path d="M-10 0q5 -8 10 0t10 0" stroke="${c}" stroke-width="3" fill="none"/>`:ty===1?`<path d="M0 -7L7 6H-7Z" fill="none" stroke="${c}" stroke-width="2.6"/>`:ty===2?`<circle r="5" fill="none" stroke="${c}" stroke-width="2.6"/>`:`<path d="M-6 0H6M0 -6V6" stroke="${c}" stroke-width="2.6"/>`;
    s+=`<g transform="translate(${f1(x)} ${f1(y)}) scale(${f1(z)} ${f1(z*.55)}) rotate(${rot})" opacity="${op}">${g}</g>`}
   s+=`<rect y="${fl-8*Math.max(sc,.5)}" width="${W}" height="${f1(8*Math.max(sc,.5))}" fill="#0A0614"/>`;
   return {bg:s,vig:`<rect width="${W}" height="${H}" fill="url(#${id}v)"/>`}}

  /* neon "GAME ON" sign on the wall: a tube for each letter, one letter flickers now and then */
  function neonSign(x,y,s=1){const id='ns'+(RID++);let o=`<defs>${blur(id+'g',8*s)}${blur(id+'h',2.5*s)}</defs><rect x="${x-20*s}" y="${y-70*s}" width="${380*s}" height="${100*s}" rx="${12*s}" fill="#07050C" opacity=".55"/>`;
   [...'GAME ON'].forEach((ch,k)=>{if(ch===' ')return;const cx=x+k*50*s+10*s;const c=k<4?PK:CY;
    o+=`<g class="nl" data-k="${k}"><text x="${cx}" y="${y}" font-family="'Silkscreen',monospace" font-size="${64*s}" fill="none" stroke="${c}" stroke-width="${7*s}" filter="url(#${id}g)" opacity=".8">${ch}</text><text x="${cx}" y="${y}" font-family="'Silkscreen',monospace" font-size="${64*s}" fill="none" stroke="${c}" stroke-width="${3.6*s}" filter="url(#${id}h)">${ch}</text><text x="${cx}" y="${y}" font-family="'Silkscreen',monospace" font-size="${64*s}" fill="none" stroke="${lt(c,.75)}" stroke-width="${1.6*s}">${ch}</text></g>`});
   return o}

  /* ---------- arcade cabinets, built as solid boxes seen from the front-right ---------- */
  const KX=.5,KY=.35;
  /* side profile: [depth from front, height above floor]; negative depth sticks out at the front */
  const PROF_C=[[12,0],[12,-250],[-26,-262],[-26,-284],[30,-302],[42,-312],[72,-482],[40,-490],[32,-566],[152,-566],[152,0]];
  const PJ=(x0,by,W,x,d,y)=>[x0+x+d*KX,by+y-d*KY];
  function affine(o,ax,ay,w,h){/* map a w x h local box onto the parallelogram o + ax*u + ay*v */return `matrix(${f1(ax[0]/w*1000)/1000} ${f1(ax[1]/w*1000)/1000} ${f1(ay[0]/h*1000)/1000} ${f1(ay[1]/h*1000)/1000} ${f1(o[0])} ${f1(o[1])})`}
  function quad(P,fill,extra=''){return `<path d="M${P.map(p=>f1(p[0])+' '+f1(p[1])).join('L')}Z" fill="${fill}" ${extra}/>`}
  function cabinet(x0,by,W,c,game,name,side=true){const id='cb'+(RID++);const P=PROF_C;const pj=(x,i)=>PJ(x0,by,W,x,P[i][0],P[i][1]);const pd=(x,d,y)=>PJ(x0,by,W,x,d,y);
   const body='#15121F',body2='#0E0C16',top='#221D30';
   let s=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${dk(c,.55)}"/><stop offset="1" stop-color="#120E1C"/></linearGradient>${blur(id+'g',14)}${blur(id+'gs',4)}<radialGradient id="${id}fl"><stop offset="0" stop-color="${c}" stop-opacity=".35"/><stop offset="1" stop-color="${c}" stop-opacity="0"/></radialGradient></defs>`;
   /* glow on the floor in front */
   s+=`<ellipse class="fglow" cx="${f1(x0+W*.5)}" cy="${f1(by+26)}" rx="${f1(W*1.1)}" ry="40" fill="url(#${id}fl)"/>`;
   s+=`<ellipse cx="${f1(x0+W*.5+40)}" cy="${f1(by-14)}" rx="${f1(W*.8)}" ry="22" fill="#000" opacity=".5"/>`;
   /* front-facing strips, back to front */
   const segs=[[8,'top'],[7,'marq'],[6,'under'],[5,'screen'],[4,'bez'],[3,'panel'],[2,'lip'],[1,'under'],[0,'lower']];
   const fills={top,marq:'#000',under:body2,screen:'#0A0A10',bez:'#1C1828',panel:'#24202F',lip:dk(c,.2),lower:body};
   segs.forEach(([i,k])=>{s+=quad([pj(0,i),pj(W,i),pj(W,i+1),pj(0,i+1)],fills[k],'stroke="#05040A" stroke-width="1"')});
   /* marquee: lit sign with the game's name and chasing bulbs */
   {const o=pj(0,8),ax=[W,0],ay=[pj(0,7)[0]-o[0],pj(0,7)[1]-o[1]];
    s+=`<g transform="${affine(o,ax,ay,200,90)}"><rect x="6" y="8" width="188" height="74" rx="4" fill="${c}"/><rect x="6" y="8" width="188" height="74" rx="4" fill="url(#${id}s)" opacity=".55"/><rect x="14" y="16" width="172" height="58" rx="3" fill="#0C0814" opacity=".6"/>
    <text x="100" y="56" font-family="'Press Start 2P',monospace" font-size="15" fill="${lt(c,.6)}" text-anchor="middle" stroke="${dk(c,.5)}" stroke-width="3" paint-order="stroke">${name}</text>`;
    for(let k=0;k<14;k++)s+=`<circle class="mq" data-k="${k}" cx="${f1(14+k*13.2)}" cy="12" r="2.6" fill="#FFF3C0"/><circle class="mq" data-k="${k+1}" cx="${f1(14+k*13.2)}" cy="78" r="2.6" fill="#FFF3C0"/>`;
    s+=`</g>`;
    s+=quad([pj(0,8),pj(W,8),pj(W,7),pj(0,7)],c,`opacity=".35" filter="url(#${id}g)"`)}
   /* screen: the game playing its demo, glowing */
   {const t0=.07,t1=.93,l=14,r=W-14;const A=pd(l,P[6][0]*t1+P[5][0]*(1-t1),P[6][1]*t1+P[5][1]*(1-t1)),B=pd(r,P[6][0]*t1+P[5][0]*(1-t1),P[6][1]*t1+P[5][1]*(1-t1)),Cc=pd(l,P[6][0]*t0+P[5][0]*(1-t0),P[6][1]*t0+P[5][1]*(1-t0));
    const ax=[B[0]-A[0],B[1]-A[1]],ay=[Cc[0]-A[0],Cc[1]-A[1]];
    s+=`<g transform="${affine(A,ax,ay,160,120)}" class="scr" data-game="${game}"><defs><clipPath id="${id}c"><rect width="160" height="120" rx="6"/></clipPath></defs><g clip-path="url(#${id}c)">${game==='comet'?cometGame():rallyGame()}<rect width="160" height="120" fill="url(#scan)"/><rect width="160" height="40" fill="#fff" opacity=".06"/></g><rect width="160" height="120" rx="6" fill="none" stroke="#000" stroke-width="3"/></g>`;
    s+=quad([A,B,[B[0]+ay[0],B[1]+ay[1]],Cc],c,`class="sglow" opacity=".22" filter="url(#${id}g)"`)}
   /* control panel: a joystick and two buttons standing up off the slanted top */
   {const on=(x,f)=>pd(x,P[3][0]+(P[4][0]-P[3][0])*f,P[3][1]+(P[4][1]-P[3][1])*f);
    const j=on(W*.3,.5);s+=`<ellipse cx="${f1(j[0])}" cy="${f1(j[1])}" rx="11" ry="4.5" fill="#05040A"/><path class="jy" data-x="${f1(j[0])}" data-y="${f1(j[1])}" d="M${f1(j[0])} ${f1(j[1])}V${f1(j[1]-24)}" stroke="#C9CED8" stroke-width="4" stroke-linecap="round"/><circle class="jb" cx="${f1(j[0])}" cy="${f1(j[1]-28)}" r="9.5" fill="#E8263A"/><circle class="jbh" cx="${f1(j[0]-3)}" cy="${f1(j[1]-31)}" r="3" fill="#fff" opacity=".7"/>`;
    [[.58,CY],[.76,YL]].forEach(([fx,bc])=>{const b=on(W*fx,.42);s+=`<ellipse cx="${f1(b[0])}" cy="${f1(b[1]+1)}" rx="8.5" ry="4" fill="${dk(bc,.5)}"/><ellipse cx="${f1(b[0])}" cy="${f1(b[1]-2)}" rx="7.5" ry="3.4" fill="${bc}"/>`})}
   /* coin door with two glowing coin slots */
   {const a=pd(W*.32,P[0][0],-150),b=pd(W*.68,P[0][0],-150);s+=`<rect x="${f1(a[0])}" y="${f1(a[1])}" width="${f1(b[0]-a[0])}" height="84" rx="4" fill="#0B0A12" stroke="#3A3548" stroke-width="2"/>`;
    [.38,.62].forEach(fx=>{const q=pd(W*fx,P[0][0],-136);s+=`<rect x="${f1(q[0]-9)}" y="${f1(q[1])}" width="18" height="26" rx="2" fill="#2A0A08"/><rect class="coin" x="${f1(q[0]-6)}" y="${f1(q[1]+3)}" width="12" height="20" rx="1.5" fill="#FF6A3A"/><rect x="${f1(q[0]-1)}" y="${f1(q[1]+6)}" width="2" height="14" fill="#2A0A08"/>`});
    const q=pd(W*.5,P[0][0],-90);s+=T(q[0],q[1],11,'1 CREDIT',{w:800,anchor:'middle',fill:'#FFB08A'})}
   /* the side panel with a big painted stripe and a glowing edge trim */
   if(side){const S=P.map((p,i)=>pj(W,i));s+=quad(S,'url(#'+id+'s)','stroke="#05040A" stroke-width="1"');
    const sp=(d,y)=>pd(W,d,y);s+=`<defs><clipPath id="${id}sc">${quad(S,'#000')}</clipPath></defs><g clip-path="url(#${id}sc)" opacity=".85">`;
    [[0,'#FF4FA8'],[26,c],[52,'#FFD23F']].forEach(([o,cc])=>{s+=quad([sp(-40,-120-o),sp(200,-330-o),sp(200,-310-o),sp(-40,-100-o)],cc)});
    s+=`</g>`;s+=`<path d="M${S.map(p=>f1(p[0])+' '+f1(p[1])).join('L')}" fill="none" stroke="${c}" stroke-width="3.4" opacity=".9"/><path d="M${S.map(p=>f1(p[0])+' '+f1(p[1])).join('L')}" fill="none" stroke="${c}" stroke-width="8" opacity=".35" filter="url(#${id}gs)"/>`}
   return s}

  /* the two demo games (all made up): a little ship dodging comets, and a pixel car on a road at sunset */
  function cometGame(){const r=rnd(9);let s=`<rect width="160" height="120" fill="#06061A"/>`;
   for(let k=0;k<24;k++)s+=`<rect class="st" data-x="${f1(r()*160)}" data-y="${f1(r()*120)}" data-v="${f1(10+r()*30)}" width="2" height="2" fill="#fff" opacity="${f1(.4+r()*.6)}"/>`;
   for(let k=0;k<4;k++)s+=`<g class="cm" data-k="${k}"><rect x="-3" y="-22" width="6" height="14" fill="#FF8A3A" opacity=".55"/><rect x="-2" y="-14" width="4" height="6" fill="#FFD23F" opacity=".8"/><rect x="-7" y="-7" width="14" height="14" fill="#8A7A9A"/><rect x="-9" y="-4" width="18" height="8" fill="#8A7A9A"/><rect x="-7" y="-7" width="6" height="6" fill="#C8BCD8"/><rect x="2" y="1" width="4" height="4" fill="#5A4A6A"/></g>`;
   s+=`<rect class="lz" width="2" height="8" fill="${LM}"/><g class="ship"><rect x="-2" y="-12" width="4" height="4" fill="#fff"/><rect x="-6" y="-8" width="12" height="4" fill="${CY}"/><rect x="-10" y="-4" width="20" height="4" fill="${CY}"/><rect x="-10" y="0" width="4" height="4" fill="${PK}"/><rect x="6" y="0" width="4" height="4" fill="${PK}"/></g>`;
   s+=`<text x="6" y="12" font-family="'Press Start 2P',monospace" font-size="7" fill="#fff">HI 042150</text><text class="sc" x="154" y="12" font-family="'Press Start 2P',monospace" font-size="7" fill="${YL}" text-anchor="end">000000</text>`;return s}
  function rallyGame(){let s=`<defs><linearGradient id="rsky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2A1050"/><stop offset="1" stop-color="#FF7A5A"/></linearGradient></defs><rect width="160" height="52" fill="url(#rsky)"/>`;
   [[80,40,16,'#FFD23F']].forEach(([x,y,r,c])=>{for(let k=-r;k<r;k+=4){const w=Math.sqrt(r*r-k*k)*2;s+=`<rect x="${f1(x-w/2)}" y="${y+k}" width="${f1(w)}" height="3" fill="${c}"/>`}});
   s+=`<path d="M0 52L20 40L36 48L58 34L80 46L100 36L124 48L144 38L160 46V52Z" fill="#3A1A5A"/>`;
   s+=`<rect y="52" width="160" height="68" fill="#2E7A3A"/><path d="M74 52H86L150 120H10Z" fill="#4A4A58"/>`;
   for(let k=0;k<6;k++)s+=`<rect class="rs" data-k="${k}" fill="#F4F0E6"/>`;
   for(let k=0;k<5;k++)s+=`<rect class="rb" data-k="${k}" fill="#E8263A"/>`;
   s+=`<g class="car"><rect x="-12" y="-14" width="24" height="6" fill="${PK}"/><rect x="-8" y="-20" width="16" height="6" fill="${CY}"/><rect x="-14" y="-8" width="28" height="6" fill="${PK}"/><rect x="-14" y="-2" width="6" height="4" fill="#111"/><rect x="8" y="-2" width="6" height="4" fill="#111"/></g>`;
   s+=`<text x="6" y="12" font-family="'Press Start 2P',monospace" font-size="7" fill="#fff">LAP 2/3</text><text class="tm" x="154" y="12" font-family="'Press Start 2P',monospace" font-size="7" fill="${YL}" text-anchor="end">0'42"</text>`;return s}
  const SCAN=`<defs><pattern id="scan" width="4" height="3" patternUnits="userSpaceOnUse"><rect width="4" height="1" fill="#000" opacity=".28"/></pattern></defs>`;

  /* a round-top stool in front of the first machine */
  function stool(x,by){return `<ellipse cx="${x}" cy="${by+2}" rx="40" ry="10" fill="#000" opacity=".5"/><ellipse cx="${x}" cy="${by-4}" rx="30" ry="8" fill="#5A5E6A"/><rect x="${x-4}" y="${by-104}" width="8" height="100" fill="#A8AEBA"/><rect x="${x-1}" y="${by-104}" width="2" height="100" fill="#fff" opacity=".5"/><ellipse cx="${x}" cy="${by-56}" rx="24" ry="6" fill="none" stroke="#A8AEBA" stroke-width="3"/><ellipse cx="${x}" cy="${by-104}" rx="40" ry="12" fill="#7A0E1A"/><path d="M${x-40} ${by-104}v-12a40 12 0 0 0 80 0v12a40 12 0 0 1 -80 0Z" fill="#A8142A"/><ellipse cx="${x}" cy="${by-116}" rx="40" ry="12" fill="#D8263E"/><ellipse cx="${x-12}" cy="${by-119}" rx="16" ry="4" fill="#fff" opacity=".3"/>`}

  function arcadeScene(){/* origin = floor line at the left; drawn for a 1920 x 1080 room */
   return `<g transform="translate(-40 -162) scale(1.15)">`+neonSign(150,330)+cabinet(110,1010,170,PK,'comet','COMET RUN',false)+cabinet(280,1010,170,CY,'rally','PIXEL RALLY',true)+stool(250,1060)+`</g>`}

  /* ---------- the window: a warm September night, the arcade across the street ---------- */
  const skin='#E8B48A',skin2='#B97E58',skin3='#8A5A3A';
  function groundY(x,ww,wh){return wh-40}
  function windowSVG([wx,wy,ww,wh]){const id='wn'+(RID++);const r=rnd(31);
   let s=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0A0A26"/><stop offset=".7" stop-color="#2A1A4E"/><stop offset="1" stop-color="#4A2A5E"/></linearGradient>${blur(id+'b',4)}</defs><rect width="${ww}" height="${wh}" fill="url(#${id}s)"/>`;
   for(let k=0;k<20;k++)s+=`<circle cx="${f1(r()*ww)}" cy="${f1(r()*wh*.35)}" r="${f1(.6+r()*.8)}" fill="#fff" opacity="${f1(.3+r()*.5)}"/>`;
   s+=`<circle cx="${f1(ww*.78)}" cy="${f1(wh*.14)}" r="18" fill="#FFF3D0"/><circle cx="${f1(ww*.78+7)}" cy="${f1(wh*.14-4)}" r="16" fill="#0E0C2A" opacity=".0"/>`;
   let x=-10;while(x<ww){const w=26+r()*30,h=wh*(.3+r()*.25),y=wh*.62-h;s+=`<rect x="${f1(x)}" y="${f1(y)}" width="${f1(w)}" height="${f1(wh-y)}" fill="#0C0A1E"/>`;for(let yy=y+6;yy<wh*.6;yy+=10)for(let xx=x+4;xx<x+w-6;xx+=8)if(r()<.25)s+=`<rect x="${f1(xx)}" y="${f1(yy)}" width="3.4" height="4.6" fill="#FFD98A" opacity="${f1(.35+r()*.5)}"/>`;x+=w+2}
   /* the arcade across the street */
   const ax=ww*.12,aw=ww*.6,ay=wh*.36;s+=`<rect x="${f1(ax)}" y="${f1(ay)}" width="${f1(aw)}" height="${f1(wh*.3)}" fill="#160E2A"/><rect x="${f1(ax+10)}" y="${f1(ay+34)}" width="${f1(aw-20)}" height="${f1(wh*.3-34)}" fill="#3A1A5A"/>`;
   [CY,PK,YL,LM].forEach((c,k)=>{s+=`<rect x="${f1(ax+18+k*aw*.22)}" y="${f1(ay+44)}" width="${f1(aw*.14)}" height="${f1(wh*.2)}" fill="#0A0814"/><rect x="${f1(ax+22+k*aw*.22)}" y="${f1(ay+50)}" width="${f1(aw*.14-8)}" height="14" fill="${c}" opacity=".85"/>`});
   s+=`<g class="arcsign"><text x="${f1(ax+aw/2)}" y="${f1(ay+24)}" font-family="'Silkscreen',monospace" font-size="20" fill="none" stroke="${PK}" stroke-width="4" text-anchor="middle" filter="url(#${id}b)">ARCADE</text><text x="${f1(ax+aw/2)}" y="${f1(ay+24)}" font-family="'Silkscreen',monospace" font-size="20" fill="#FFD0E8" text-anchor="middle">ARCADE</text></g>`;
   s+=`<rect y="${f1(wh*.66)}" width="${ww}" height="${f1(wh*.34)}" fill="#24203A"/><rect y="${f1(wh-46)}" width="${ww}" height="46" fill="#3A3650"/><rect y="${f1(wh-46)}" width="${ww}" height="3" fill="#56506E"/>`;
   for(let k=0;k<6;k++)s+=`<rect x="${f1(k*ww/5)}" y="${f1(wh*.78)}" width="${f1(ww/10)}" height="4" fill="#FFE9A0" opacity=".5"/>`;
   return `<rect x="${wx-14}" y="${wy-14}" width="${ww+28}" height="${wh+28}" rx="6" fill="#1E1630"/><svg x="${wx}" y="${wy}" width="${ww}" height="${wh}" overflow="hidden">${s}<g class="walkers" data-w="${ww}" data-h="${wh}">${walkers()}</g></svg><rect x="${wx+ww/2-4}" y="${wy}" width="8" height="${wh}" fill="#1E1630"/><rect x="${wx}" y="${wy+wh/2-4}" width="${ww}" height="8" fill="#1E1630"/><rect x="${wx-22}" y="${wy+wh+12}" width="${ww+44}" height="10" rx="3" fill="#1E1630"/>`}

  /* ---------- who goes past the window (feet on the ground at y=0, facing left). All made up, no real game characters ---------- */
  function leg(x,hipY,col,shoe,ph,w=6,sw=null){return `<g class="lg" data-x="${x}" data-p="${hipY}" data-ph="${ph}"><path d="M${x} ${hipY} L${x} -4" stroke="${col}" stroke-width="${w}" stroke-linecap="round"/>${sw||`<path d="M${x+3} -1 h-${w+4} a3.5 3.5 0 0 1 0 -6 h${w+1}Z" fill="${shoe}" stroke="${shoe}" stroke-width="2" stroke-linejoin="round"/>`}</g>`}
  function rleg(x,hipY,col,shoe,ph,w=7){return `<g class="rl" data-x="${x}" data-p="${hipY}" data-ph="${ph}"><path class="lp" d="M${x} ${hipY}L${x} -4" stroke="${col}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" fill="none"/><ellipse class="lf" cx="${x-3}" cy="-3" rx="7" ry="3.6" fill="${shoe}"/></g>`}
  function arm(x,sy,len,col,ph,w=6,hand=''){return `<g class="ar" data-x="${x}" data-p="${sy}" data-ph="${ph}"><path d="M${x} ${sy} L${x-2} ${sy+len}" stroke="${col}" stroke-width="${w}" stroke-linecap="round"/>${hand}</g>`}
  const face=(x,y,r,hair,sk=skin)=>`<circle cx="${x}" cy="${y}" r="${r}" fill="${sk}"/><circle cx="${x-r*.45}" cy="${y-r*.1}" r="${r*.13}" fill="#2A1A10"/><path d="M${x-r*.6} ${y+r*.35} q${r*.25} ${r*.2} ${r*.5} 0" stroke="#7A3A20" stroke-width="1.3" fill="none" stroke-linecap="round"/>`+(hair?`<path d="M${x-r} ${y-r*.1} C${x-r} ${y-r*1.3} ${x+r*1.1} ${y-r*1.4} ${x+r} ${y+r*.2} C${x+r*.6} ${y-r*.3} ${x} ${y-r*.6} ${x-r} ${y-r*.1}Z" fill="${hair}"/>`:'');
  /* 1. a clunky pizza-place animatronic: our own green robot frog mascot with glowing eyes, walking stiffly */
  function wMascot(){const G='#3FA46A',G2='#2A7A4A',M='#8A929E';
   const foot=x=>`<ellipse cx="${x-3}" cy="-4" rx="9" ry="5" fill="${G2}"/>`;
   let s=leg(-8,-46,M,'',1,8,foot(-8))+`<circle cx="-8" cy="-26" r="4" fill="#5A606C"/>`;
   s+=`<path d="M-24 -96 Q-26 -50 -16 -44 H16 Q26 -50 22 -96Z" fill="${G}" stroke="${G2}" stroke-width="2"/><ellipse cx="-4" cy="-66" rx="13" ry="16" fill="#CFE8B0"/>`;
   [[-18,-90],[16,-90],[-18,-52],[16,-52]].forEach(([x,y])=>{s+=`<circle cx="${x}" cy="${y}" r="1.8" fill="#5A606C"/>`});
   s+=leg(8,-46,M,'',0,8,foot(8))+`<circle cx="8" cy="-26" r="4" fill="#5A606C"/>`;
   s+=arm(-20,-90,38,M,0,6,`<circle cx="-22" cy="-50" r="6" fill="${G}"/>`);
   s+=`<rect x="-6" y="-104" width="12" height="10" fill="${M}"/>`;
   s+=`<path d="M-30 -116 Q-30 -142 0 -142 Q28 -142 26 -116 Q24 -102 0 -102 Q-28 -102 -30 -116Z" fill="${G}" stroke="${G2}" stroke-width="2"/>`;
   s+=`<path d="M-31 -116 H24" stroke="#1A1A22" stroke-width="2.4"/><path d="M-28 -116 v5 h5 v-5 M-20 -116 v5 h5 v-5 M-12 -116 v5 h5 v-5" fill="#F4F0E6" stroke="#F4F0E6" stroke-width="1"/><circle cx="20" cy="-116" r="3" fill="${M}"/>`;
   s+=`<circle cx="-18" cy="-140" r="9" fill="${G}" stroke="${G2}" stroke-width="2"/><circle cx="4" cy="-140" r="9" fill="${G}" stroke="${G2}" stroke-width="2"/>`;
   s+=`<g transform="rotate(12 -6 -146)"><path d="M-16 -146 L-6 -178 L4 -146Z" fill="#FFD23F"/><path d="M-13 -156 L1 -156 L-1 -164 L-11 -164Z" fill="#FF4FA8"/><circle cx="-6" cy="-179" r="4" fill="#FF4FA8"/></g><rect x="-2" y="-84" width="18" height="10" rx="2" fill="#F4F0E6"/><path d="M1 -79h12" stroke="#E8263A" stroke-width="2"/>`;
   s+=`<g class="eyes"><circle cx="-18" cy="-140" r="7" fill="#FFE14A" opacity=".35"/><circle cx="4" cy="-140" r="7" fill="#FFE14A" opacity=".35"/><circle cx="-18" cy="-140" r="3.6" fill="#FFF6B0"/><circle cx="4" cy="-140" r="3.6" fill="#FFF6B0"/></g>`;
   s+=`<path d="M-14 -96 L-6 -92 L-14 -88Z M2 -96 L-6 -92 L2 -88Z" fill="#E8263A"/><circle cx="-6" cy="-92" r="2.4" fill="#A8142A"/>`;
   s+=arm(18,-90,38,M,1,6,`<circle cx="16" cy="-50" r="6" fill="${G}"/>`);
   return `<g class="wk" data-off="0" data-s=".82" data-a="12" data-f="5">${s}</g>`}
  /* 2. an armoured space soldier in our own colours: orange plates, big cyan visor, heavy steps */
  function wSpace(){const O='#E8742A',O2='#A84A14',D='#2A2C34';
   const boot=x=>`<path d="M${x+5} -1 h-17 a4 4 0 0 1 0 -8 h4 v-8 h12Z" fill="#4A4E5A"/>`;
   let s=leg(-7,-50,D,'',1,10,boot(-7))+`<rect x="-13" y="-36" width="12" height="9" rx="3" fill="${O}"/>`;
   s+=arm(-20,-94,36,D,0,8,`<rect x="-28" y="-66" width="12" height="14" rx="3" fill="${O}"/><circle cx="-23" cy="-56" r="5" fill="#4A4E5A"/>`);
   s+=`<rect x="10" y="-100" width="18" height="40" rx="5" fill="#4A4E5A"/><path d="M-20 -104 H18 L16 -52 H-18Z" fill="${D}"/><path d="M-18 -102 H16 L13 -72 Q0 -64 -15 -72Z" fill="${O}" stroke="${O2}" stroke-width="1.6"/><path d="M-15 -60 H13 V-50 H-15Z" fill="${O2}"/><rect x="-6" y="-92" width="12" height="4" rx="2" fill="${O2}"/>`;
   s+=leg(7,-50,D,'',0,10,boot(7))+`<rect x="1" y="-36" width="12" height="9" rx="3" fill="${O}"/>`;
   s+=`<path d="M-26 -106 Q-24 -94 -12 -96 L-10 -104Z" fill="${O}" stroke="${O2}"/><path d="M22 -106 Q22 -94 12 -96 L10 -104Z" fill="${O}" stroke="${O2}"/>`;
   s+=`<path d="M-17 -112 Q-18 -140 2 -140 Q20 -140 18 -112 Q16 -104 0 -104 Q-16 -104 -17 -112Z" fill="#E8E4DA" stroke="#9AA2B0" stroke-width="1.6"/><path d="M-20 -128 Q-20 -114 -4 -114 Q6 -114 6 -122 Q6 -132 -6 -132 Q-18 -132 -20 -128Z" fill="#1FA8C8"/><path d="M-16 -128 Q-12 -130 -6 -129" stroke="#B8F6FF" stroke-width="2.4" fill="none" stroke-linecap="round"/><path d="M0 -140 V-112" stroke="${O}" stroke-width="4"/>`;
   s+=arm(18,-94,36,D,1,8,`<rect x="10" y="-66" width="12" height="14" rx="3" fill="${O}"/><circle cx="16" cy="-56" r="5" fill="#4A4E5A"/>`);
   s+=`<path d="M24 -100 L30 -128" stroke="#8A929E" stroke-width="2"/><circle class="blink" cx="30" cy="-129" r="3" fill="#9CFF57"/><circle class="blink" cx="-4" cy="-82" r="2.4" fill="#3FE0FF"/>`;
   return `<g class="wk" data-off="6" data-s=".86" data-a="18" data-f="5.5">${s}</g>`}
  /* 3. a pixel car driving past, made of chunky squares, headlights on */
  function wCar(){const P=5;const g=['....oooooo......','...occccwwo.....','..occcccwwwo....','oorrrrrrrrrrrrrr','orrrrrrrrrrrrrrr','yrrrrrrrrrrrrrrk','orrkkkrrrrrkkkrr','...kgk.....kgk..'];const cm={o:'#14121C',c:'#1FA8C8',w:'#B8F6FF',r:'#E8263A',y:'#FFE14A',k:'#14121C',g:'#8A929E'};
   let s=`<path d="M-4 -18 L-90 -34 L-90 2 Z" fill="#FFE9A0" opacity=".18"/>`;g.forEach((r,j)=>[...r].forEach((ch,i)=>{if(ch!=='.')s+=`<rect x="${i*P}" y="${-g.length*P+j*P}" width="${P+.3}" height="${P+.3}" fill="${cm[ch]}"/>`}));
   return `<g class="wk" data-off="12" data-s="1" data-v="2.2" data-car="1">${s}</g>`}
  /* 4. a cartoon hero in a blue cap and yellow jacket, sprinting and jumping now and then */
  function wRunner(){const Y='#FFC93A',B='#2E5AC8';
   let s=rleg(-4,-42,'#2A3A6A','#E8263A',1,7)+arm(6,-86,30,Y,0,7,`<circle cx="4" cy="-55" r="4" fill="${skin}"/>`);
   s+=`<path d="M-14 -90 Q-18 -64 -12 -42 H10 Q14 -66 10 -90Z" fill="${Y}"/><path d="M-2 -88 V-44" stroke="#C89A20" stroke-width="2"/>`;
   s+=`<rect x="6" y="-86" width="10" height="30" rx="4" fill="#2E5AC8"/><path d="M-12 -78 H10 M-13 -70 H11" stroke="#fff" stroke-width="2.4" opacity=".8"/>`;
   s+=rleg(4,-42,'#2A3A6A','#E8263A',0,7)+face(-2,-102,12,'#5A3418',skin2);
   s+=`<path d="M-14 -106 Q-14 -122 0 -122 Q14 -122 12 -106Z" fill="${B}"/><path d="M-14 -107 H-28 Q-28 -103 -14 -103Z" fill="${B}"/><path d="M0 -119l1.5 3.2 3.5.4-2.6 2.4.7 3.5-3.1-1.8-3.1 1.8.7-3.5-2.6-2.4 3.5-.4Z" fill="#fff"/>`;
   s+=arm(-8,-86,30,Y,1,7,`<circle cx="-10" cy="-55" r="4" fill="${skin2}"/>`);
   return `<g class="wk" data-off="18" data-s=".9" data-v="1.7" data-a="42" data-f="12" data-j="1" data-lean="-8">${s}</g>`}
  /* 5. a hooded adventurer with a round shield and a sword on their back, running, cloak flapping */
  function wHero(){const R='#B8282E',R2='#7A1418',Br='#7A4A24';
   let s=`<path class="cloak" d="M8 -96 Q30 -70 26 -40 L10 -46Z" fill="${R2}"/>`;
   s+=`<path d="M10 -112 L-12 -70" stroke="#C9CED8" stroke-width="3.4" stroke-linecap="round"/><path d="M6 -104 L16 -98" stroke="#8A6418" stroke-width="4" stroke-linecap="round"/><circle cx="12" cy="-112" r="3" fill="#E8B84A"/>`;
   s+=rleg(-4,-44,'#3A2A1A','#2A1A10',1,7)+arm(4,-88,30,Br,0,6,`<circle cx="2" cy="-57" r="4" fill="${skin}"/>`);
   s+=`<path d="M-13 -92 H11 L14 -46 H-16Z" fill="${Br}"/><rect x="-14" y="-62" width="28" height="5" fill="#3A2A1A"/><rect x="-3" y="-63" width="6" height="7" fill="#E8B84A"/>`;
   s+=`<rect x="-14" y="-60" width="9" height="10" rx="2" fill="#5A3418"/><path d="M-8 -96 q4 -6 8 0 q-4 6 -8 0Z" fill="#E8B84A"/>`;
   s+=`<circle cx="16" cy="-74" r="15" fill="#8A5A2A" stroke="#C9CED8" stroke-width="3"/><circle cx="16" cy="-74" r="4" fill="#C9CED8"/>`;
   s+=rleg(4,-44,'#3A2A1A','#2A1A10',0,7)+face(-2,-102,11,'#E8C060');
   s+=`<path d="M-14 -100 Q-16 -124 0 -124 Q16 -122 14 -96 L10 -92 Q10 -110 0 -112 Q-10 -112 -12 -98Z" fill="${R}"/><path d="M14 -100 Q22 -96 28 -88" stroke="${R}" stroke-width="5" fill="none" stroke-linecap="round"/>`;
   s+=arm(-6,-88,30,Br,1,6,`<circle cx="-8" cy="-57" r="4" fill="${skin}"/>`);
   return `<g class="wk" data-off="24" data-s=".9" data-v="1.6" data-a="40" data-f="11" data-lean="-6">${s}</g>`}
  function walkers(){return wMascot()+wSpace()+wCar()+wRunner()+wHero()}


  /** The two shapes: wide (TVs, computers) and tall (phones, no window) */
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1590, 140, 280, 350] },
    tall: { w: 390, h: 844, win: null },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t) } which poses everything at t seconds.
   * opts: { layout: "wide"|"tall", align: "xMidYMax slice" }
   */
  function build(svg, opts) {
    const name = (opts && opts.layout) || "wide";
    const L = LAYOUTS[name];
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", (opts && opts.align) || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win);
    const scene = name === "tall"
      ? `<g transform="translate(-6 316) scale(.5)">${cabinet(110, 1010, 170, PK, "comet", "COMET RUN", false) + cabinet(280, 1010, 170, CY, "rally", "PIXEL RALLY", true)}</g><g transform="translate(232 480) scale(.36)">${neonSign(0, 0)}</g>`
      : arcadeScene();
    svg.innerHTML = SCAN + rm.bg + scene + rm.vig;
    return pose(svg);
  }

  /* gathers every moving part, and returns at(t) */
  function pose(root) {
    const q = (s) => [...root.querySelectorAll(s)];
    const bulbs = q(".mq"), letters = q(".nl"), glows = q(".sglow,.fglow"), coins = q(".coin"), signs = q(".arcsign");
    const screens = q(".scr").map((S) => ({
      S, game: S.dataset.game,
      stars: [...S.querySelectorAll(".st")], comets: [...S.querySelectorAll(".cm")], ship: S.querySelector(".ship"), laser: S.querySelector(".lz"), score: S.querySelector(".sc"),
      stripes: [...S.querySelectorAll(".rs")], posts: [...S.querySelectorAll(".rb")], car: S.querySelector(".car"), time: S.querySelector(".tm"),
    }));
    // star speeds that move a whole number of screens (120 px) per loop, so the video joins up
    screens.forEach((s) => s.stars.forEach((e) => { e.dataset.v = String(Math.max(1, Math.round(+e.dataset.v * LOOP / 120)) * 120 / LOOP); }));
    const walkers = q(".walkers").map((W) => ({ ww: +W.dataset.w, wh: +W.dataset.h, wk: [...W.querySelectorAll(".wk")] }));
    const s3 = wq(3), s24 = wq(2.4), s13 = wq(1.3), s09 = wq(0.9);
    function at(t) {
      const tl = ((t % LOOP) + LOOP) % LOOP;
      // marquee bulbs chase round the sign
      bulbs.forEach((b) => b.setAttribute("opacity", (Math.floor(t * 6) + (+b.dataset.k)) % 3 === 0 ? "1" : ".25"));
      // the N of "GAME ON" flickers for half a second every 10 seconds
      letters.forEach((l) => { let o = 1; if (+l.dataset.k === 5) { const c = tl % 10; if (c < 0.5) o = Math.sin(c * 60) > 0 ? 1 : 0.15; } l.setAttribute("opacity", o); });
      glows.forEach((g) => g.setAttribute("opacity", f1((g.classList.contains("sglow") ? 0.22 : 1) * (0.85 + 0.15 * Math.sin(t * s3 + (+g.getAttribute("cx") || 0))))));
      coins.forEach((c) => c.setAttribute("opacity", f1(0.7 + 0.3 * Math.sin(t * s24))));
      signs.forEach((a) => a.setAttribute("opacity", (tl % 5) < 0.3 && Math.sin(tl * 50) > 0 ? ".3" : "1"));
      screens.forEach((s) => {
        if (s.game === "comet") {
          s.stars.forEach((e) => { e.setAttribute("x", e.dataset.x); e.setAttribute("y", f1((+e.dataset.y + tl * e.dataset.v) % 120)); });
          const sx = 80 + Math.sin(t * s13) * 52; s.ship.setAttribute("transform", `translate(${f1(sx)} 108)`);
          // comets fall every 6 seconds, each time in a new lane (5 lanes, then the pattern repeats with the loop)
          s.comets.forEach((g) => { const k = +g.dataset.k; const d = tl * 25 + k * 34; const cy = (d % 150) - 20; const cx = 20 + ((k * 47 + (Math.floor(d / 150) % 5) * 61) % 120); g.setAttribute("transform", `translate(${f1(cx)} ${f1(cy)})`); });
          const lp = (tl * 1.6) % 1; const lx = 80 + Math.sin((t - lp / 1.6) * s13) * 52; s.laser.setAttribute("x", f1(lx - 1)); s.laser.setAttribute("y", f1(100 - lp * 110));
          s.score.textContent = String(Math.floor(tl * 50) * 10).padStart(6, "0");
        } else {
          const ph = (tl * 1.4) % 1;
          s.stripes.forEach((e) => { const k = +e.dataset.k; const z = (k + ph) / 6; const y = 52 + 68 * z * z, w = 1 + 5 * z, h = 2 + 8 * z * z; e.setAttribute("x", f1(80 - w / 2)); e.setAttribute("y", f1(y)); e.setAttribute("width", f1(w)); e.setAttribute("height", f1(h)); });
          s.posts.forEach((e) => { const k = +e.dataset.k; const z = ((k + tl * 1.2) % 5) / 5; const y = 52 + 68 * z * z; const hw = 6 + 64 * z * z; const side = k % 2 ? 1 : -1; e.setAttribute("x", f1(80 + side * (hw + 4 * z) - (side < 0 ? 6 * z : 0))); e.setAttribute("y", f1(y)); e.setAttribute("width", f1(2 + 6 * z)); e.setAttribute("height", f1(2 + 6 * z)); });
          s.car.setAttribute("transform", `translate(${f1(80 + Math.sin(t * s09) * 22)} 112)`);
          s.time.textContent = `0'${String(Math.floor(tl)).padStart(2, "0")}"`;
        }
      });
      // the parade past the window: one every 6 seconds, the whole parade every 30
      walkers.forEach(({ ww, wh, wk }) => {
        const Pw = LOOP, v = (ww + 300) / 9;
        wk.forEach((g) => {
          const off = +g.dataset.off, sc = 1.5 * (+g.dataset.s), vf = +(g.dataset.v || 1), amp = +(g.dataset.a || 22), fq = +(g.dataset.f || 7), lean = +(g.dataset.lean || 0);
          const tt = ((tl + Pw - off) % Pw); const x = ww + 150 - tt * v * vf;
          const y = groundY(x, ww, wh) + 2; const car = g.dataset.car;
          let bob = car ? (Math.floor(tt * 8) % 2) : Math.abs(Math.sin(tt * fq)) * (amp > 30 ? 4 : 1.5);
          const air = !!g.dataset.j && (tt % 2.4) < 0.7;
          if (air) bob += Math.sin(Math.PI * (tt % 2.4) / 0.7) * 34;
          g.setAttribute("transform", `translate(${f1(x)} ${f1(y - bob)}) scale(${sc}) rotate(${lean} 0 -40)`);
          const ph = tt * fq;
          g.querySelectorAll(".lg").forEach((l) => { const sw = air ? (+l.dataset.ph ? -30 : 35) : Math.sin(ph + (+l.dataset.ph) * Math.PI) * amp; l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} ${l.dataset.p})`); });
          g.querySelectorAll(".rl").forEach((l) => {
            const X = +l.dataset.x, H = +l.dataset.p, Ln = (-H - 4) / 2; const p = ph + (+l.dataset.ph) * Math.PI;
            const th = (air ? (+l.dataset.ph ? -25 : 40) : Math.sin(p) * amp) * Math.PI / 180; const bend = (air ? 60 : Math.max(0, Math.cos(p)) * 85 + 10) * Math.PI / 180;
            const kx = X - Math.sin(th) * Ln, ky = H + Math.cos(th) * Ln; const sa = th - bend; const fx = kx - Math.sin(sa) * Ln, fy = ky + Math.cos(sa) * Ln;
            l.querySelector(".lp").setAttribute("d", `M${X} ${H}L${f1(kx)} ${f1(ky)}L${f1(fx)} ${f1(fy)}`);
            const fe = l.querySelector(".lf"); fe.setAttribute("cx", f1(fx - 3)); fe.setAttribute("cy", f1(fy + 1)); fe.setAttribute("transform", `rotate(${f1(-sa * 180 / Math.PI * 0.4)} ${f1(fx)} ${f1(fy)})`);
          });
          g.querySelectorAll(".ar").forEach((l) => { const sw = -Math.sin(ph + (+l.dataset.ph) * Math.PI) * amp * 0.9; l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} ${l.dataset.p})`); });
          const ck = g.querySelector(".cloak"); if (ck) { const w = Math.sin(tt * 14) * 6; ck.setAttribute("d", `M8 -96 Q${f1(32 + w)} -72 ${f1(30 + w * 1.4)} -42 L10 -46Z`); }
          g.querySelectorAll(".blink").forEach((e) => e.setAttribute("opacity", Math.sin(tt * 5) > 0 ? "1" : ".25"));
          const ey = g.querySelector(".eyes"); if (ey) ey.setAttribute("opacity", (tt % 4) < 0.25 && Math.sin(tt * 60) > 0 ? ".2" : "1");
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

  G.BenflixArcade = { LOOP, season, greeting, LINE, build, play, LAYOUTS };
})(typeof window !== "undefined" ? window : globalThis);
