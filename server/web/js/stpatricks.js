/* Benflix St. Patrick's Day: the Who's watching room (approved 2026-11-17, thread "Design a St. Patrick's Day theme for Benflix").
 *
 * A dim, warm room with moss-green walls and wood panelling, and green, white and gold cloth flags hanging along
 * the top of the wall (kept clear of the logo). Bottom left, away from the profiles, is a wooden side table with a
 * linen runner: a black iron pot overflowing with gold coins (they glint, and every few seconds one flips up off
 * the top and lands again), a brown teapot with steam curling from its spout, and a clay pot of real shamrocks
 * with little white clover flowers, gently swaying. Through the window (wide screens only) it rains softly on a
 * village street of white cottages with coloured doors and green hills, while a little parade walks past: a piper
 * in a green kilt playing the bagpipes, a kid waving a shamrock flag, someone walking a dog in a green bow tie,
 * and a grown-up in a tall green hat holding balloons. Rain runs down the glass too.
 *
 * 10 to 16 March ("week"): rain, and "Who's watching?" with "3 days to St. Patrick's Day" under it.
 * 17 March ("day"): the rain stops, a rainbow comes out over the hills, the greeting is "Happy St. Patrick's
 * Day!" and little shamrocks drift down across the screen all day.
 *
 * Like feast.js, the same drawing is used live on the web and by tools/stpatricks/record.js, which records it as
 * looping videos for the TV and phone app. Everything is a function of the time t (seconds) and repeats every
 * LOOP seconds, so a video of one loop joins up with no jump.
 * The drawing code comes straight from the approved design sheet (stpatricks-2-whos-watching.html).
 */
(function (G) {
  "use strict";
  /** 30 seconds: the parade walks past once */
  const LOOP = 30;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;
  /** a rate that fits a whole number of cycles into one loop, close to r cycles a second */
  const rq = (r) => Math.max(1, Math.round(r * LOOP)) / LOOP;
  /** a speed (units a second) that moves a whole number of [span]s in one loop, close to v */
  const sq = (v, span) => Math.max(1, Math.round(v * LOOP / span)) * span / LOOP;

  // ---------- dates ----------
  /** 10 to 17 March, the week that ends on St. Patrick's Day (Valentine's ends 14 Feb, Easter week comes after) */
  const season = (d) => d.getMonth() === 2 && d.getDate() >= 10 && d.getDate() <= 17;
  /** "week" 10 to 16 March (rain), "day" on 17 March (rainbow and falling shamrocks) */
  const variant = (d) => (d.getMonth() === 2 && d.getDate() === 17 ? "day" : "week");
  /** the big words on Who's watching */
  const greeting = (d) => (variant(d) === "day" ? "Happy St. Patrick’s Day!" : "Who’s watching?");
  /** the line under the greeting in the week before: "3 days to St. Patrick's Day" */
  const countdown = (d) => {
    if (!season(d) || variant(d) === "day") return "";
    const days = 17 - d.getDate();
    return days === 1 ? "St. Patrick’s Day is tomorrow" : `${days} days to St. Patrick’s Day`;
  };

  // ---------- drawing kit (from the approved design sheet) ----------
let UID=0;const u=p=>p+(UID++)+'_';
const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
const f1=v=>(+v).toFixed(1);
const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const GOLD=['#FFF1C2','#EBC35A','#9A6E1C'],SILV=['#FFFFFF','#C9D2DE','#6E7A8C'];
function gold(p,c=GOLD,cx=.35,cy=.3){return `<radialGradient id="${p}" cx="${cx}" cy="${cy}" r=".85"><stop offset="0" stop-color="${c[0]}"/><stop offset=".5" stop-color="${c[1]}"/><stop offset="1" stop-color="${c[2]}"/></radialGradient>`}
function lgold(p,c=GOLD){return `<linearGradient id="${p}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${c[0]}"/><stop offset=".45" stop-color="${c[1]}"/><stop offset="1" stop-color="${c[2]}"/></linearGradient>`}
const glint=(x,y,s,op=1)=>`<path d="M${f1(x)} ${f1(y-s)}Q${f1(x+s*.14)} ${f1(y-s*.14)} ${f1(x+s)} ${f1(y)}Q${f1(x+s*.14)} ${f1(y+s*.14)} ${f1(x)} ${f1(y+s)}Q${f1(x-s*.14)} ${f1(y+s*.14)} ${f1(x-s)} ${f1(y)}Q${f1(x-s*.14)} ${f1(y-s*.14)} ${f1(x)} ${f1(y-s)}Z" fill="#fff" opacity="${op}"/>`;
function pin(cx,cy,r){const p=u('pn');return `<defs>${gold(p)}</defs><circle cx="${cx}" cy="${f1(cy+r*.35)}" r="${r}" fill="#000" opacity=".35"/><circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#${p})" stroke="#5A400C" stroke-width="1.1"/><circle cx="${f1(cx-r*.35)}" cy="${f1(cy-r*.35)}" r="${f1(r*.32)}" fill="#fff" opacity=".85"/>`}
function chain(x1,y1,x2,y2,w=2.6){const L=Math.hypot(x2-x1,y2-y1),n=Math.max(2,Math.round(L/(w*2)));const ang=Math.atan2(y2-y1,x2-x1)*180/Math.PI;let s='';
 for(let i=0;i<n;i++){const t=(i+.5)/n,x=x1+(x2-x1)*t,y=y1+(y2-y1)*t;
  s+=i%2?`<rect x="${f1(x-w*1.2)}" y="${f1(y-w*.32)}" width="${f1(w*2.4)}" height="${f1(w*.64)}" rx="${f1(w*.3)}" fill="#A67A22" transform="rotate(${f1(ang)} ${f1(x)} ${f1(y)})"/>`
  :`<ellipse cx="${f1(x)}" cy="${f1(y)}" rx="${f1(w*1.3)}" ry="${f1(w*.8)}" transform="rotate(${f1(ang)} ${f1(x)} ${f1(y)})" fill="none" stroke="#F2D27A" stroke-width="${f1(w*.6)}"/>`}return s}
const sh=(id,s)=>`<filter id="${id}" x="-40%" y="-40%" width="180%" height="180%"><feDropShadow dx="0" dy="${f1(s*.6)}" stdDeviation="${s}" flood-color="#000" flood-opacity=".5"/></filter>`;


const GRN=['#8FE3A6','#2E9E58','#0E4A28'];
function ggrad(p,c=GRN,cx=.35,cy=.3){return gold(p,c,cx,cy)}
/* a heart-shaped clover leaf: point at (0,0), notch at the outer end, length L, pointing up before rotation */
const leafD=L=>`M0 0C${f1(-L*.95)} ${f1(-L*.3)} ${f1(-L*.85)} ${f1(-L*1.12)} ${f1(-L*.3)} ${f1(-L*1.02)}Q${f1(-L*.04)} ${f1(-L*.98)} 0 ${f1(-L*.8)}Q${f1(L*.04)} ${f1(-L*.98)} ${f1(L*.3)} ${f1(-L*1.02)}C${f1(L*.85)} ${f1(-L*1.12)} ${f1(L*.95)} ${f1(-L*.3)} 0 0Z`;
function clover(cx,cy,L,n=4,rot=0,o={}){const p=u('cl');let s=`<defs><radialGradient id="${p}" cx=".5" cy=".85" r="1"><stop offset="0" stop-color="${o.c0||'#2F8F4A'}"/><stop offset=".7" stop-color="${o.c1||'#3FAE5C'}"/><stop offset="1" stop-color="${o.c2||'#2A8445'}"/></radialGradient></defs>`;
 for(let k=0;k<n;k++){const a=rot+k*360/n;
  s+=`<g transform="translate(${f1(cx)} ${f1(cy)}) rotate(${f1(a)})"><path d="${leafD(L)}" fill="${o.flat||`url(#${p})`}" stroke="${o.edge||'#14532B'}" stroke-width="${o.sw||1.2}" stroke-linejoin="round"/>`;
  if(!o.flat){/* pale crescent marking that real clover leaves have, and the middle vein */
   s+=`<path d="M${f1(-L*.42)} ${f1(-L*.5)}Q0 ${f1(-L*.28)} ${f1(L*.42)} ${f1(-L*.5)}" stroke="#CDEFC9" stroke-width="${f1(L*.09)}" fill="none" opacity=".55" stroke-linecap="round"/>
   <path d="M0 ${f1(-L*.06)}L0 ${f1(-L*.78)}" stroke="#1D6B37" stroke-width="1.1" opacity=".8"/>
   <path d="M0 ${f1(-L*.4)}L${f1(-L*.38)} ${f1(-L*.72)}M0 ${f1(-L*.4)}L${f1(L*.38)} ${f1(-L*.72)}M0 ${f1(-L*.6)}L${f1(-L*.2)} ${f1(-L*.88)}M0 ${f1(-L*.6)}L${f1(L*.2)} ${f1(-L*.88)}" stroke="#1D6B37" stroke-width=".7" opacity=".55"/>
   <ellipse cx="${f1(-L*.36)}" cy="${f1(-L*.82)}" rx="${f1(L*.12)}" ry="${f1(L*.06)}" fill="#fff" opacity=".35" transform="rotate(-30 ${f1(-L*.36)} ${f1(-L*.82)})"/>`}
  s+=`</g>`}
 return s+`<circle cx="${f1(cx)}" cy="${f1(cy)}" r="${f1(L*.09)}" fill="${o.flat?o.edge||'#14532B':'#1E6B38'}"/>`}
const cord=(d,c='#1E8A47',w=2.6)=>`<path d="${d}" stroke="#0A2A16" stroke-width="${w+1.6}" fill="none" stroke-linecap="round" opacity=".6"/><path d="${d}" stroke="${c}" stroke-width="${w}" fill="none" stroke-linecap="round"/>`;

const EM='#16603A';
function smooth(P,closed){const n=P.length;let d=`M${f1(P[0][0])} ${f1(P[0][1])}`;const g=i=>closed?P[(i+n)%n]:P[Math.max(0,Math.min(n-1,i))];
 for(let i=0;i<(closed?n:n-1);i++){const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);d+=`C${f1(p1[0]+(p2[0]-p0[0])/6)} ${f1(p1[1]+(p2[1]-p0[1])/6)} ${f1(p2[0]-(p3[0]-p1[0])/6)} ${f1(p2[1]-(p3[1]-p1[1])/6)} ${f1(p2[0])} ${f1(p2[1])}`}return d+(closed?'Z':'')}
let RID=0;let STATE='week';
function groundY(x,ww,wh){return wh-34+Math.sin(x*.018)*2}
const RB=['#E5484D','#F28C28','#F2C94C','#3CC46A','#4A90E2','#8E5BD8'];

/* ---------- the room: dim moss-green walls, wood panelling, bunting along the top ---------- */
function room(W2,H2,win,bunting=true){const id='rm'+(RID++);const fl=H2*.8;const sc=W2/1920;
 let s=`<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#08110D"/><stop offset="1" stop-color="#11211A"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#241A12"/><stop offset="1" stop-color="#0C0907"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".6"/></radialGradient><radialGradient id="${id}g" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#FFC874" stop-opacity=".22"/><stop offset="1" stop-color="#FFC874" stop-opacity="0"/></radialGradient></defs>`;
 s+=`<rect width="${W2}" height="${H2}" fill="url(#${id}w)"/>`;for(let x=0;x<W2;x+=W2/24)s+=`<rect x="${f1(x)}" y="0" width="${f1(W2/48)}" height="${fl}" fill="#fff" opacity=".012"/>`;
 /* wood panelling on the lower wall */
 const py=fl-H2*.2;s+=`<rect y="${f1(py)}" width="${W2}" height="${f1(H2*.2)}" fill="#1A140E" opacity=".75"/><rect y="${f1(py)}" width="${W2}" height="${f1(6*Math.max(sc,.4))}" fill="#3A2A1A"/>`;
 for(let x=W2/40;x<W2;x+=W2/10)s+=`<rect x="${f1(x)}" y="${f1(py+H2*.03)}" width="${f1(W2/10-W2/20)}" height="${f1(H2*.13)}" rx="3" fill="none" stroke="#000" stroke-opacity=".35" stroke-width="${f1(3*Math.max(sc,.4))}"/>`;
 if(win)s+=windowSVG(win);
 s+=`<rect y="${fl}" width="${W2}" height="${H2-fl}" fill="url(#${id}f)"/>`;for(let k=1;k<6;k++)s+=`<path d="M0 ${f1(fl+(H2-fl)*k/6)} H${W2}" stroke="#000" stroke-opacity=".25" stroke-width="2"/>`;
 s+=`<rect y="${fl-10*Math.max(sc,.4)}" width="${W2}" height="${f1(10*Math.max(sc,.4))}" fill="#0E0B08"/>`;
 if(bunting){/* green, white and gold cloth flags on a string, kept right of the logo */
  const sw=W2<600?[[20,30,W2-20,30,26]]:[[470*sc,40*sc,1000*sc,40*sc,70*sc],[1000*sc,40*sc,1530*sc,40*sc,70*sc]];const FC=['#2E9E58','#EDE6D6','#E6B640'];
  sw.forEach(([x1,y1,x2,y2,sag],si)=>{const N=Math.round((x2-x1)/(W2<600?30:46*sc));const pt=t=>[x1+(x2-x1)*t,y1+(y2-y1)*t+sag*4*t*(1-t)];
   let wire='';for(let k=0;k<=24;k++){const [x,y]=pt(k/24);wire+=(k?'L':'M')+f1(x)+' '+f1(y)}s+=`<path d="${wire}" stroke="#D8CCB4" stroke-width="${f1(2.4*Math.max(sc,.5))}" fill="none" opacity=".8"/>`;
   for(let k=0;k<N;k++){const a=pt(k/N),b=pt((k+.82)/N);const mx=(a[0]+b[0])/2,my=(a[1]+b[1])/2,h=(W2<600?26:48*sc);const c=FC[(k+si)%3];
    s+=`<g class="pen" data-x="${f1(mx)}" data-y="${f1(my)}" data-k="${k+si*7}"><path d="M${f1(a[0])} ${f1(a[1])}L${f1(b[0])} ${f1(b[1])}L${f1(mx+1)} ${f1(my+h)}Z" fill="${c}"/><path d="M${f1(mx)} ${f1(my)}L${f1(b[0])} ${f1(b[1])}L${f1(mx+1)} ${f1(my+h)}Z" fill="#000" opacity=".16"/>${c==='#2E9E58'&&W2>=600?clover(mx,my+h*.36,h*.16,3,0,{flat:'#EDE6D6',edge:'#EDE6D6',sw:.5}):''}</g>`}})}
 return {bg:s,vig:`<rect width="${W2}" height="${H2}" fill="url(#${id}v)"/>`,glow:(x,y,r)=>`<ellipse class="roomglow" cx="${x}" cy="${y}" rx="${r}" ry="${f1(r*.7)}" fill="url(#${id}g)"/>`}}

/* ---------- the window: an Irish village street, soft rain all week, a rainbow on the day ---------- */
function windowSVG([wx,wy,ww,wh]){const id='wn'+(RID++);const r=rnd(31);const day=STATE==='day';
 let s=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1">${day?'<stop offset="0" stop-color="#3E6E9A"/><stop offset=".7" stop-color="#9CC2D6"/><stop offset="1" stop-color="#E8D9B0"/>':'<stop offset="0" stop-color="#2A3440"/><stop offset=".7" stop-color="#55646E"/><stop offset="1" stop-color="#7D8A88"/>'}</linearGradient></defs><rect width="${ww}" height="${wh}" fill="url(#${id}s)"/>`;
 /* clouds */
 for(let k=0;k<4;k++){const cx=r()*ww,cy=wh*(.08+r()*.18),w=60+r()*60;s+=`<ellipse cx="${f1(cx)}" cy="${f1(cy)}" rx="${f1(w)}" ry="${f1(w*.28)}" fill="${day?'#F4F4EE':'#3A4650'}" opacity="${day?.75:.85}"/>`}
 if(day)RB.forEach((c,i)=>{const rr=ww*.62-i*7;s+=`<path class="rbw" d="M${f1(ww*.58-rr)} ${f1(wh*.62)}A${f1(rr)} ${f1(rr)} 0 0 1 ${f1(ww*.58+rr)} ${f1(wh*.62)}" stroke="${c}" stroke-width="7.4" fill="none" opacity=".6"/>`});
 /* two rows of green hills with stone walls */
 s+=`<path d="M0 ${f1(wh*.5)}Q${f1(ww*.3)} ${f1(wh*.36)} ${f1(ww*.6)} ${f1(wh*.48)}T${ww} ${f1(wh*.44)}V${wh}H0Z" fill="${day?'#5E9A55':'#3F6A42'}"/><path d="M0 ${f1(wh*.6)}Q${f1(ww*.4)} ${f1(wh*.5)} ${ww} ${f1(wh*.6)}V${wh}H0Z" fill="${day?'#3F7A3E':'#2C5230'}"/>`;
 for(let k=0;k<3;k++){const y=wh*(.5+k*.04);s+=`<path d="M${f1(ww*(.05+k*.2))} ${f1(y+6)}Q${f1(ww*(.3+k*.2))} ${f1(y-4)} ${f1(ww*(.55+k*.2))} ${f1(y+8)}" stroke="#7E8478" stroke-width="2" stroke-dasharray="3 2" fill="none" opacity=".8"/>`}
 /* a row of cottages with coloured doors */
 const DC=['#C8323A','#2B5FA8','#E6B640','#2E9E58'];let x=-6;let k=0;while(x<ww){const w=46+r()*14,h=40+r()*10,y=wh-48-h;
  s+=`<rect x="${f1(x)}" y="${f1(y)}" width="${f1(w)}" height="${f1(h+12)}" fill="${['#EDE6D6','#E8D9B8','#DCE3DE'][k%3]}"/><path d="M${f1(x-4)} ${f1(y)}L${f1(x+w/2)} ${f1(y-22)}L${f1(x+w+4)} ${f1(y)}Z" fill="${k%2?'#4A3A30':'#5C5650'}"/><rect x="${f1(x+w*.62)}" y="${f1(y-28)}" width="6" height="12" fill="#5C5650"/>
  <rect x="${f1(x+w*.12)}" y="${f1(y+h*.3)}" width="${f1(w*.24)}" height="${f1(h*.32)}" fill="${day?'#BFD8E6':'#FFD98A'}" opacity="${day?.8:.9}"/><rect x="${f1(x+w*.56)}" y="${f1(y+h*.32)}" width="${f1(w*.24)}" height="${f1(h*.68+12)}" fill="${DC[k%4]}"/>`;x+=w+10;k++}
 /* wet road */
 let g=`M0 ${f1(groundY(0,ww,wh))}`;for(let x=0;x<=ww;x+=10)g+=`L${x} ${f1(groundY(x,ww,wh))}`;g+=`V${wh} H0Z`;
 s+=`<path d="${g}" fill="${day?'#8A8E86':'#4E5658'}"/><path d="M0 ${f1(wh-14)} H${ww}" stroke="#fff" stroke-width="2" stroke-dasharray="14 12" opacity=".35"/>`;
 if(!day)for(let k=0;k<5;k++)s+=`<ellipse cx="${f1(r()*ww)}" cy="${f1(wh-8-r()*18)}" rx="${f1(10+r()*14)}" ry="2" fill="#AFC0C6" opacity=".35"/>`;
 let rain='';if(!day)for(let k=0;k<44;k++)rain+=`<line class="rn" data-x="${f1(r()*ww)}" data-y="${f1(r()*wh)}" data-v="${f1(160+r()*80)}" data-wh="${wh}" stroke="#C9D6DC" stroke-width="1" opacity=".5"/>`;
 let drops='';if(!day)for(let k=0;k<14;k++){const dx=r()*ww,dy=r()*wh;drops+=`<g class="drp" data-x="${f1(dx)}" data-y="${f1(dy)}" data-v="${f1(6+r()*14)}" data-wh="${wh}"><ellipse rx="2.6" ry="3.4" fill="#E8F0F2" opacity=".35"/><ellipse cx="-.8" cy="-1.2" rx=".8" ry="1" fill="#fff" opacity=".8"/></g>`}
 return `<rect x="${wx-14}" y="${wy-14}" width="${ww+28}" height="${wh+28}" rx="6" fill="#20170F"/><svg x="${wx}" y="${wy}" width="${ww}" height="${wh}" overflow="hidden">${s}<g class="walkers" data-w="${ww}" data-h="${wh}">${walkers()}</g>${rain}${drops}</svg><rect x="${wx+ww/2-4}" y="${wy}" width="8" height="${wh}" fill="#20170F"/><rect x="${wx}" y="${wy+wh/2-4}" width="${ww}" height="8" fill="#20170F"/><rect x="${wx-22}" y="${wy+wh+12}" width="${ww+44}" height="10" rx="3" fill="#20170F"/>`}

/* ---------- people (and a dog) walking past: feet on the ground at y=0, facing left ---------- */
const skin='#E8B48A',skin2='#B97E58';
function leg(x,hipY,col,shoe,ph,w=6){return `<g class="lg" data-x="${x}" data-p="${hipY}" data-ph="${ph}"><path d="M${x} ${hipY} L${x} -3" stroke="${col}" stroke-width="${w}" stroke-linecap="round"/><path d="M${x+2} -1 h-8 a3 3 0 0 1 0 -5 h6Z" fill="${shoe}" stroke="${shoe}" stroke-width="2" stroke-linejoin="round"/></g>`}
function face(x,y,r,hair,sk=skin){return `<circle cx="${x}" cy="${y}" r="${r}" fill="${sk}"/><circle cx="${x-r*.45}" cy="${y-r*.1}" r="${r*.13}" fill="#2A1A10"/><path d="M${x-r*.6} ${y+r*.35} q${r*.25} ${r*.2} ${r*.5} 0" stroke="#7A3A20" stroke-width="1.3" fill="none" stroke-linecap="round"/><circle cx="${x-r*.15}" cy="${y+r*.25}" r="${r*.2}" fill="#E07A6A" opacity=".45"/>`+(hair?`<path d="M${x-r} ${y-r*.1} C${x-r} ${y-r*1.3} ${x+r*1.1} ${y-r*1.4} ${x+r} ${y+r*.2} C${x+r*.6} ${y-r*.3} ${x} ${y-r*.6} ${x-r} ${y-r*.1}Z" fill="${hair}"/>`:'')}
function person(x,o){const H=o.h||1;let s=leg(x-5,-44*H,o.legs,o.shoe||'#15121A',0)+leg(x+5,-44*H,o.legs,o.shoe||'#15121A',1);
 s+=`<path d="M${x-14} ${-90*H} h28 l5 ${48*H} h-38Z" fill="${o.coat}"/><path d="M${x} ${-90*H} v${46*H}" stroke="#000" stroke-opacity=".18" stroke-width="2"/>`;
 if(o.scarf)s+=`<rect x="${x-12}" y="${-92*H}" width="24" height="7" rx="3.5" fill="${o.scarf}"/>`;
 return s+face(x,-102*H,11,o.hair,o.skin||skin)+(o.hat||'')}
function wPiper(){/* a piper in a green tartan kilt playing the bagpipes */
 let s=leg(-5,-40,'#EDE6D6','#15121A',0,5.5)+leg(5,-40,'#EDE6D6','#15121A',1,5.5);
 s+=`<path d="M-15 -52 h30 l4 18 h-38Z" fill="#1E6B3E"/><path d="M-13 -52v18M-5 -52v18M3 -52v18M11 -52v18" stroke="#0E3A20" stroke-width="2"/><path d="M-17 -46h34M-17 -40h34" stroke="#C8323A" stroke-width="1.2" opacity=".8"/>`;
 s+=`<path d="M-14 -92 h28 l2 40 h-32Z" fill="#14231A"/><rect x="-14" y="-56" width="28" height="5" fill="#2A1A10"/>`;
 /* drones over the shoulder with tassels, bag under the arm, chanter in both hands */
 s+=`<g class="drn"><path d="M4 -88 L22 -134 M8 -86 L30 -126 M10 -82 L36 -116" stroke="#2A1A10" stroke-width="3.4" stroke-linecap="round"/><path d="M20 -134h5M28 -126h5M34 -116h5" stroke="#E6B640" stroke-width="3"/><path d="M14 -110 q6 10 14 4" stroke="#2E9E58" stroke-width="3" fill="none"/></g>`;
 s+=`<ellipse cx="4" cy="-76" rx="13" ry="10" fill="#2E7A4A"/><path d="M-6 -80 L-22 -62" stroke="#2A1A10" stroke-width="3.4" stroke-linecap="round"/><path d="M-6 -86 L-20 -70 M-2 -78 L-20 -64" stroke="#14231A" stroke-width="6" stroke-linecap="round"/><circle cx="-20" cy="-70" r="3.2" fill="${skin}"/><circle cx="-21" cy="-64" r="3.2" fill="${skin}"/>`;
 s+=face(0,-102,11,'#7A3A1A')+`<path d="M-9 -108 l-6 -8 a4 4 0 0 1 6 -6 Q4 -124 14 -112 Z" fill="#1E3A2A"/><circle cx="-12" cy="-118" r="3" fill="#C8323A"/><path d="M-6 -96 L-12 -94" stroke="#2A1A10" stroke-width="2"/>`;
 return `<g class="wk" data-off="0" data-s=".95">${s}</g>`}
function wFlagKid(){/* a kid waving a small green flag with a white shamrock */
 let s=leg(-4,-30,'#2E3A5A','#2E9E58',0)+leg(4,-30,'#2E3A5A','#2E9E58',1);
 s+=`<rect x="-11" y="-62" width="22" height="34" rx="9" fill="#E6B640"/>`;
 s+=`<path d="M-4 -56 L-14 -76" stroke="#D4A030" stroke-width="7" stroke-linecap="round"/><circle cx="-15" cy="-78" r="3.6" fill="${skin2}"/><path d="M-15 -74 L-15 -112" stroke="#8A6A40" stroke-width="2"/>`;
 s+=`<path class="flag" d="" fill="#2E9E58" stroke="#1E6B3E" stroke-width="1"/><g class="flagc"></g>`;
 s+=face(0,-72,10,'#2A1A10',skin2)+`<path d="M-11 -76 C-11 -90 11 -90 11 -76Z" fill="#2E9E58"/><rect x="-12" y="-78" width="24" height="5" rx="2.5" fill="#EDE6D6"/><circle cx="0" cy="-88" r="4" fill="#EDE6D6"/>`;
 return `<g class="wk" data-off="9" data-s=".95">${s}</g>`}
function dogLeg(x,hy,ph,c){return `<g class="dl" data-x="${x}" data-p="${hy}" data-ph="${ph}"><path d="M${x} ${hy} L${x} -6 L${x-3} -1" stroke="${c}" stroke-width="4.4" stroke-linecap="round" stroke-linejoin="round" fill="none"/></g>`}
function wDog(){/* someone walking a dog in a green bow tie; dog legs move in diagonal pairs */
 let s=person(18,{legs:'#3A3A48',coat:'#7A4A2A',scarf:'#2E9E58',hair:'#B88A4A',hat:`<path d="M7 -108 C7 -124 29 -124 29 -108Z" fill="#2E7A4A"/><rect x="5" y="-110" width="26" height="4" rx="2" fill="#2E7A4A"/>`});
 s+=`<path d="M12 -84 L4 -64" stroke="#6A3E22" stroke-width="7" stroke-linecap="round"/><circle cx="3" cy="-62" r="3.4" fill="${skin}"/><path class="lead" d="M3 -62 Q-14 -40 -30 -34" stroke="#C8323A" stroke-width="1.6" fill="none"/>`;
 const dc='#C98A4A';s+=`<g class="dog">`+dogLeg(-28,-22,1,dk(dc,.25))+dogLeg(-52,-22,0,dk(dc,.25))+`<path class="tail" d="" stroke="${dc}" stroke-width="4" fill="none" stroke-linecap="round"/><ellipse cx="-40" cy="-26" rx="18" ry="9" fill="${dc}"/>`+dogLeg(-30,-22,0,dc)+dogLeg(-50,-22,1,dc)+
  `<g class="dhead"><ellipse cx="-58" cy="-38" rx="8" ry="7.5" fill="${dc}"/><ellipse cx="-66" cy="-35" rx="5" ry="3.6" fill="${lt(dc,.25)}"/><circle cx="-70" cy="-36" r="1.8" fill="#1A1008"/><circle cx="-60" cy="-40" r="1.4" fill="#1A1008"/><path d="M-54 -44 q4 2 3 12 q-4 -2 -6 -9Z" fill="${dk(dc,.35)}"/></g><path d="M-52 -32 l-5 -3 v6Z M-52 -32 l5 -3 v6Z" fill="#2E9E58"/><circle cx="-52" cy="-32" r="1.6" fill="#1E6B3E"/></g>`;
 return `<g class="wk" data-off="16" data-s=".95">${s}</g>`}
function wBalloon(){/* a grown-up in a big green felt hat, holding green and gold balloons */
 let s=person(0,{legs:'#1E2A3A',coat:'#2E5A7A',scarf:'#E6B640',hair:'#5A3418',skin:skin2,hat:`<path d="M-15 -110 h30 l-2 -4 h-26Z" fill="#14532B"/><path d="M-11 -112 L-13 -140 Q0 -136 13 -140 L11 -112Z" fill="#2E9E58"/><rect x="-12" y="-122" width="24" height="5" fill="#141414"/><rect x="-4" y="-123" width="8" height="7" fill="none" stroke="#E6B640" stroke-width="1.6"/>`});
 s+=`<path d="M-6 -84 L-16 -64" stroke="#244A66" stroke-width="7" stroke-linecap="round"/><circle cx="-17" cy="-62" r="3.6" fill="${skin2}"/>`;
 s+=`<g class="bls">${[[-34,-168,'#2E9E58'],[-12,-184,'#E6B640'],[-52,-190,'#EDE6D6']].map(([x,y,c])=>`<path d="M-17 -62 Q${(x-17)/2} ${(y-62)/2} ${x} ${y+16}" stroke="#E8E2D4" stroke-width="1" fill="none"/><ellipse cx="${x}" cy="${y}" rx="13" ry="16" fill="${c}"/><ellipse cx="${x-4}" cy="${y-6}" rx="3.4" ry="5" fill="#fff" opacity=".5"/><path d="M${x-2} ${y+16} l2 -3 l2 3Z" fill="${c}"/>`).join('')}</g>`;
 return `<g class="wk" data-off="23" data-s=".92">${s}</g>`}
function walkers(){return wPiper()+wFlagKid()+wDog()+wBalloon()}

/* ---------- the lucky corner: a wooden side table ---------- */
function coinE(x,y,rx,p,o=''){return `<ellipse cx="${f1(x)}" cy="${f1(y)}" rx="${f1(rx)}" ry="${f1(rx*.42)}" fill="url(#${p})" stroke="#7A5814" stroke-width="1" ${o}/><ellipse cx="${f1(x)}" cy="${f1(y-rx*.04)}" rx="${f1(rx*.66)}" ry="${f1(rx*.26)}" fill="none" stroke="#FFF1C2" stroke-width="1" opacity=".7"/>`}
function potOfGoldBig(cx,by){const p=u('pb');const R=rnd(5);
 let s=`<defs>${gold(p+'c',GOLD,.35,.3)}<radialGradient id="${p}i" cx=".32" cy=".3" r=".9"><stop offset="0" stop-color="#5A6068"/><stop offset=".4" stop-color="#23272C"/><stop offset="1" stop-color="#08090A"/></radialGradient>${blur(p+'b',8)}</defs>`;
 /* warm glow off the gold */
 s+=`<ellipse class="potglow" cx="${cx}" cy="${by-140}" rx="120" ry="70" fill="#FFD27A" opacity=".2" filter="url(#${p}b)"/>`;
 /* legs */
 [[-44,6],[0,10],[44,6]].forEach(([dx,dy])=>s+=`<path d="M${cx+dx-7} ${by-14+dy} l3 ${14-dy} h8 l3 ${dy-14}Z" fill="#14171A"/>`);
 /* back rim and the gold heap */
 s+=`<ellipse cx="${cx}" cy="${by-118}" rx="74" ry="15" fill="#0A0B0C"/>`;
 const heap=[];for(let row=0;row<5;row++){const n=7-row;const y=by-120-row*10;const w=64-row*12;for(let k=0;k<n;k++){const x=cx-w+(2*w)*(k+.5)/n+(R()-.5)*6;heap.push([x,y+(R()-.5)*4,10+R()*2.5])}}
 heap.forEach(([x,y,r])=>s+=coinE(x,y,r,p+'c'));
 /* iron body */
 s+=`<path d="M${cx-70} ${by-114}C${cx-98} ${by-80} ${cx-88} ${by-18} ${cx-40} ${by-12}L${cx+40} ${by-12}C${cx+88} ${by-18} ${cx+98} ${by-80} ${cx+70} ${by-114}Z" fill="url(#${p}i)" stroke="#000" stroke-width="1.4"/>`;
 s+=`<path d="M${cx-62} ${by-96}C${cx-80} ${by-70} ${cx-74} ${by-40} ${cx-52} ${by-28}" stroke="#8A9098" stroke-width="5" fill="none" opacity=".35" stroke-linecap="round"/>`;
 /* rolled lip in front */
 s+=`<path d="M${cx-76} ${by-118}A76 16 0 0 0 ${cx+76} ${by-118}" stroke="#2E3338" stroke-width="10" fill="none"/><path d="M${cx-74} ${by-120}A74 14 0 0 0 ${cx+74} ${by-120}" stroke="#6A7078" stroke-width="2" fill="none" opacity=".6"/>`;
 /* ear handles */
 s+=`<path d="M${cx-76} ${by-108}q-16 4 -12 20 q4 6 10 2" stroke="#1A1D20" stroke-width="5" fill="none"/><path d="M${cx+76} ${by-108}q16 4 12 20 q-4 6 -10 2" stroke="#1A1D20" stroke-width="5" fill="none"/>`;
 /* coins that spilled onto the table, and one leaning on the pot */
 [[-96,-3,11],[-70,1,10],[86,-1,11],[104,3,9]].forEach(([dx,dy,r])=>s+=coinE(cx+dx,by+dy,r,p+'c'));
 s+=`<g transform="rotate(-14 ${cx+70} ${by})"><ellipse cx="${cx+72}" cy="${by-12}" rx="5" ry="12" fill="#9A6E1C"/><ellipse cx="${cx+70}" cy="${by-12}" rx="5" ry="12" fill="url(#${p}c)" stroke="#7A5814"/></g>`;
 /* a coin that sometimes flips up off the top of the heap, and glints */
 s+=`<g class="flip" data-x="${cx+8}" data-y="${by-162}"><ellipse rx="11" ry="4.6" fill="url(#${p}c)" stroke="#7A5814"/></g>`;
 [[cx-30,by-150],[cx+26,by-140],[cx-8,by-170],[cx+50,by-128]].forEach(([x,y],k)=>s+=`<g class="glt" data-k="${k}" data-x="${f1(x)}" data-y="${f1(y)}">${glint(0,0,9)}</g>`);
 return s}
function teapot(cx,by){const p=u('tp');let s=`<defs><radialGradient id="${p}" cx=".35" cy=".3" r=".85"><stop offset="0" stop-color="#9A5A36"/><stop offset=".45" stop-color="#5A2E18"/><stop offset="1" stop-color="#2A140A"/></radialGradient></defs>`;
 s+=`<path d="M${cx-46} ${by-50}C${cx-70} ${by-56} ${cx-78} ${by-80} ${cx-90} ${by-100}L${cx-78} ${by-103}C${cx-70} ${by-86} ${cx-62} ${by-76} ${cx-44} ${by-76}Z" fill="#5A2E18" stroke="#1A0A04" stroke-width="1.2"/><ellipse cx="${cx-84}" cy="${by-101}" rx="7" ry="3" fill="#2A140A" transform="rotate(-16 ${cx-84} ${by-101})"/>`;
 s+=`<path d="M${cx+44} ${by-74}c34 -6 38 40 4 46" stroke="#2A140A" stroke-width="11" fill="none"/><path d="M${cx+44} ${by-74}c34 -6 38 40 4 46" stroke="#6A3A1E" stroke-width="5" fill="none"/>`;
 s+=`<path d="M${cx-54} ${by-44}C${cx-58} ${by-96} ${cx+58} ${by-96} ${cx+54} ${by-44}C${cx+52} ${by-12} ${cx+30} ${by} ${cx} ${by}C${cx-30} ${by} ${cx-52} ${by-12} ${cx-54} ${by-44}Z" fill="url(#${p})" stroke="#1A0A04" stroke-width="1.2"/>`;
 s+=`<ellipse cx="${cx}" cy="${by-84}" rx="30" ry="7" fill="#3A1C0E" stroke="#1A0A04"/><path d="M${cx-24} ${by-86}Q${cx} ${by-110} ${cx+24} ${by-86}" fill="#5A2E18" stroke="#1A0A04"/><circle cx="${cx}" cy="${by-102}" r="6" fill="#6A3A1E" stroke="#1A0A04"/>`;
 s+=`<path d="M${cx-34} ${by-62}Q${cx-30} ${by-78} ${cx-14} ${by-82}" stroke="#fff" stroke-width="5" opacity=".35" fill="none" stroke-linecap="round"/><path d="M${cx-48} ${by-40}Q${cx} ${by-30} ${cx+48} ${by-40}" stroke="#E6B640" stroke-width="2.4" fill="none" opacity=".75"/>`;
 [0,1,2].forEach(k=>s+=`<path class="ms" data-k="${k}" data-x="${cx-82}" data-y="${by-106}" d="" stroke="#E8E4DA" stroke-width="5" fill="none" stroke-linecap="round" opacity="0"/>`);
 return s}
function shamrockPot(cx,by){const p=u('sp');const R=rnd(9);
 let s=`<defs><linearGradient id="${p}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#8A4228"/><stop offset=".35" stop-color="#C8683E"/><stop offset="1" stop-color="#6A2E1A"/></linearGradient></defs>`;
 /* leaves first (behind the rim at the back) */
 const ty=by-80;let lv='';const pts=[];for(let k=0;k<34;k++){const a=R()*Math.PI,rr=Math.sqrt(R());pts.push([cx+Math.cos(a)*62*rr*(R()<.5?-1:1),ty-12-Math.sin(a)*58*rr,13+R()*6,R()*120])}
 pts.sort((a,b)=>a[1]-b[1]);
 pts.forEach(([x,y,L,rot],k)=>{lv+=`<g class="swy" data-x="${f1(x)}" data-y="${f1(ty)}" data-k="${k}"><path d="M${f1(cx+(x-cx)*.3)} ${ty} Q${f1(x)} ${f1(ty-10)} ${f1(x)} ${f1(y)}" stroke="#2E7A3E" stroke-width="1.6" fill="none"/>${clover(x,y,L,3,rot,{c0:'#2A7E40',c1:'#3FAE5C',c2:'#2A8445'})}</g>`});
 [[cx-24,ty-74],[cx+30,ty-66],[cx+4,ty-86]].forEach(([x,y],k)=>{lv+=`<g class="swy" data-x="${x}" data-y="${ty}" data-k="${k+40}"><path d="M${x} ${ty} Q${x+4} ${(ty+y)/2} ${x} ${y}" stroke="#2E7A3E" stroke-width="1.4" fill="none"/>${[0,1,2,3,4,5].map(j=>{const a=j/6*Math.PI*2;return `<ellipse cx="${f1(x+Math.cos(a)*4)}" cy="${f1(y+Math.sin(a)*3)}" rx="3" ry="2.2" fill="#F4F0E4" transform="rotate(${f1(a*57)} ${f1(x+Math.cos(a)*4)} ${f1(y+Math.sin(a)*3)})"/>`}).join('')}<circle cx="${x}" cy="${y}" r="2" fill="#E6D8A0"/></g>`});
 s+=lv;
 s+=`<path d="M${cx-46} ${ty}L${cx-38} ${by}H${cx+38}L${cx+46} ${ty}Z" fill="url(#${p})" stroke="#4A1E10" stroke-width="1.2"/><rect x="${cx-52}" y="${ty-6}" width="104" height="18" rx="3" fill="url(#${p})" stroke="#4A1E10" stroke-width="1.2"/><ellipse cx="${cx}" cy="${ty-6}" rx="50" ry="5" fill="#2A1A10"/><path d="M${cx-34} ${ty+18}L${cx-30} ${by-6}" stroke="#fff" stroke-width="4" opacity=".18"/>`;
 return s}
function tableScene(){/* local: floor at y=0, table top at -300 */
 const p=u('tb');let s=`<defs><linearGradient id="${p}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7A5232"/><stop offset="1" stop-color="#4A2E1A"/></linearGradient><linearGradient id="${p}l" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#3A2414"/><stop offset=".4" stop-color="#7A5232"/><stop offset="1" stop-color="#2E1C10"/></linearGradient></defs>`;
 s+=`<ellipse cx="0" cy="2" rx="270" ry="16" fill="#000" opacity=".45"/>`;
 [-205,205].forEach(x=>s+=`<path d="M${x-13} -262 L${x-8} 0 h16 L${x+13} -262Z" fill="url(#${p}l)" stroke="#1E120A"/><ellipse cx="${x}" cy="-200" rx="13" ry="5" fill="#6A4628"/><ellipse cx="${x}" cy="-120" rx="11" ry="4" fill="#6A4628"/>`);
 s+=`<rect x="-200" y="-74" width="400" height="12" fill="#4A2E1A" stroke="#1E120A"/>`;
 s+=`<rect x="-230" y="-286" width="460" height="26" fill="url(#${p}w)" stroke="#1E120A"/><rect x="-252" y="-306" width="504" height="22" rx="4" fill="#8A5E38" stroke="#1E120A"/><path d="M-248 -302 H248" stroke="#C89A6A" stroke-width="2" opacity=".5"/>`;
 /* linen runner hanging over the front edge with a green stripe and fringe */
 s+=`<path d="M-40 -306 H120 V-252 H-40Z" fill="#E8E0CC" stroke="#B8AE96"/><rect x="-40" y="-276" width="160" height="7" fill="#2E9E58"/>`;for(let x=-36;x<=116;x+=6)s+=`<path d="M${x} -252 v9" stroke="#D8CEB6" stroke-width="2"/>`;
 s+=potOfGoldBig(-132,-304)+teapot(46,-306)+shamrockPot(176,-306);
 return s}

function cloverFall(W,H,n){if(STATE!=='day')return '';const r=rnd(23);return `<g class="conf">${Array.from({length:n},(_,k)=>{const L=W/(W<600?70:150);return `<g data-x="${f1(r()*W)}" data-v="${f1(.5+r()*.7)}" data-d="${f1(r()*6)}" data-h="${H}">${clover(0,0,L,k%5?3:4,0,{flat:['#3DBE6C','#2E9E58','#7FD39A','#E6B640'][k%4],edge:'#0E4A28',sw:.5})}</g>`}).join('')}</g>`}

  /** The two shapes: wide (TVs, computers) and tall (phones, no window) */
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1590, 150, 280, 340], table: [300, 1016, 0.82], glow: [300, 780, 360], fall: 70 },
    tall: { w: 390, h: 844, win: null, table: [200, 812, 0.62], glow: [195, 720, 200], fall: 30 },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t) } which poses everything at t seconds.
   * opts: { layout: "wide"|"tall", variant: "week"|"day" (default: today), align: "xMidYMax slice" }
   */
  function build(svg, opts) {
    const L = LAYOUTS[(opts && opts.layout) || "wide"];
    STATE = (opts && opts.variant) || variant(new Date());
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", (opts && opts.align) || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win);
    svg.innerHTML = rm.bg + rm.glow(...L.glow) + `<g transform="translate(${L.table[0]} ${L.table[1]}) scale(${L.table[2]})">${tableScene()}</g>` + rm.vig + cloverFall(L.w, L.h, L.fall);
    return pose(svg);
  }

  /* gathers every moving part, and returns at(t). Speeds are rounded so that everything repeats every LOOP seconds */
  function pose(root) {
    const q = (s) => [...root.querySelectorAll(s)];
    const pens = q(".pen"), glts = q(".glt"), flips = q(".flip"), swy = q(".swy"), ms = q(".ms"), glows = q(".roomglow"), pg = q(".potglow");
    const rain = q(".rn").map((c) => { const H = +c.dataset.wh; return { c, x: +c.dataset.x, y: +c.dataset.y, H, v: sq(+c.dataset.v, H + 20) }; });
    const drops = q(".drp").map((c) => { const H = +c.dataset.wh; return { c, x: +c.dataset.x, y: +c.dataset.y, H, v: sq(+c.dataset.v, H + 10) }; });
    const conf = q(".conf > g").map((c) => { const H = +c.dataset.h; return { c, x: +c.dataset.x, d: +c.dataset.d, H, v: sq(+c.dataset.v * H * 0.045, H + 40) }; });
    const walkers = q(".walkers").map((W) => ({ ww: +W.dataset.w, wh: +W.dataset.h, wk: [...W.querySelectorAll(".wk")] }));
    const s13 = wq(1.3), s17 = wq(1.7), s09 = wq(0.9), s1 = wq(1), s11 = wq(1.1), s12 = wq(1.2), steam = rq(0.35), spin = Math.round(70 * LOOP / 360) * 360 / LOOP;
    /** a coin flips up off the pot every 7.5 seconds (4 times a loop) */
    const FLIP = LOOP / 4;
    function at(t) {
      pens.forEach((g) => { const k = +g.dataset.k; g.setAttribute("transform", `rotate(${f1(Math.sin(t * s13 + k * 0.9) * 4)} ${g.dataset.x} ${g.dataset.y})`); });
      glts.forEach((g) => { const k = +g.dataset.k; const a = Math.max(0, Math.sin(t * s17 + k * 2.3)); g.setAttribute("transform", `translate(${g.dataset.x} ${g.dataset.y}) scale(${Math.pow(a, 6).toFixed(3)})`); });
      flips.forEach((g) => {
        const a = (t % FLIP) / FLIP, x = +g.dataset.x, y = +g.dataset.y;
        if (a < 0.16) { const e = a / 0.16; const h = Math.sin(e * Math.PI) * 46; g.setAttribute("transform", `translate(${x} ${f1(y - h)}) scale(1 ${Math.cos(e * Math.PI * 4).toFixed(2)})`); }
        else g.setAttribute("transform", `translate(${x} ${y})`);
      });
      swy.forEach((g) => { const k = +g.dataset.k; g.setAttribute("transform", `rotate(${f1(Math.sin(t * s09 + k * 0.7) * 2.2)} ${g.dataset.x} ${g.dataset.y})`); });
      ms.forEach((m) => {
        const k = +m.dataset.k, x = +m.dataset.x, y = +m.dataset.y; const a = ((t * steam + k / 3) % 1);
        m.setAttribute("d", `M${f1(x + (k - 1) * 3)} ${f1(y - a * 50)} c${f1(6 * Math.sin(t * s1 + k))} -10 ${f1(-6 * Math.sin(t * s13 + k))} -20 ${f1(2 * Math.sin(t * s1 + k))} -30`);
        m.setAttribute("opacity", (Math.sin(a * Math.PI) * 0.35).toFixed(3));
      });
      glows.forEach((g) => g.setAttribute("opacity", (0.9 + 0.1 * Math.sin(t * s13)).toFixed(3)));
      pg.forEach((g) => g.setAttribute("opacity", (0.17 + 0.06 * Math.sin(t * s11)).toFixed(3)));
      rain.forEach((R) => { const yy = ((R.y + t * R.v) % (R.H + 20)) - 10; R.c.setAttribute("x1", f1(R.x + yy * 0.12)); R.c.setAttribute("y1", f1(yy)); R.c.setAttribute("x2", f1(R.x + yy * 0.12 + 1.4)); R.c.setAttribute("y2", f1(yy + 11)); });
      drops.forEach((D) => { const yy = ((D.y + t * D.v) % (D.H + 10)) - 5; D.c.setAttribute("transform", `translate(${D.x} ${f1(yy)})`); });
      conf.forEach((C) => { const y = ((C.d * C.H * 0.17 + t * C.v) % (C.H + 40)) - 20; C.c.setAttribute("transform", `translate(${f1(C.x + Math.sin(t * s12 + C.d) * 14)} ${f1(y)}) rotate(${f1((t * spin + C.d * 60) % 360)})`); });
      walkers.forEach(({ ww, wh, wk }) => {
        const Pw = LOOP, v = (ww + 300) / 9;
        wk.forEach((g) => {
          const off = +g.dataset.off, sc = 1.5 * (+g.dataset.s); const tt = ((t + Pw - off) % Pw); const x = ww + 150 - tt * v;
          const y = groundY(x, ww, wh) + 2; const bob = Math.abs(Math.sin(tt * 7)) * 1.5;
          g.setAttribute("transform", `translate(${f1(x)} ${f1(y - bob)}) scale(${sc})`);
          g.querySelectorAll(".lg").forEach((l) => { const sw = Math.sin(tt * 7 + (+l.dataset.ph) * Math.PI) * 22; l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} ${l.dataset.p})`); });
          g.querySelectorAll(".dl").forEach((l) => { const sw = Math.sin(tt * 10.5 + (+l.dataset.ph) * Math.PI) * 26; l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} ${l.dataset.p})`); });
          const dg = g.querySelector(".dog");
          if (dg) {
            dg.setAttribute("transform", `translate(0 ${f1(-Math.abs(Math.sin(tt * 10.5)) * 1.6)})`);
            g.querySelector(".tail").setAttribute("d", `M-24 -30 q8 -6 ${f1(8 + Math.sin(tt * 14) * 5)} ${f1(-16)}`);
            g.querySelector(".dhead").setAttribute("transform", `rotate(${f1(Math.sin(tt * 10.5) * 3)} -54 -32)`);
          }
          const dr = g.querySelector(".drn"); if (dr) dr.setAttribute("transform", `rotate(${f1(Math.sin(tt * 7) * 2)} 4 -86)`);
          const bl = g.querySelector(".bls"); if (bl) bl.setAttribute("transform", `rotate(${f1(Math.sin(tt * 2) * 4)} -17 -62)`);
          const fl = g.querySelector(".flag");
          if (fl) {
            const w = (k) => Math.sin(tt * 9 - k * 1.2) * 3;
            fl.setAttribute("d", `M-15 -112 Q-24 ${f1(-114 + w(1))} -34 ${f1(-112 + w(2))} L-34 ${f1(-96 + w(2))} Q-24 ${f1(-98 + w(1))} -15 -96Z`);
            g.querySelector(".flagc").innerHTML = clover(-24.5, -104 + w(1) * 0.6, 3.6, 3, 0, { flat: "#EDE6D6", edge: "#EDE6D6", sw: 0.4 });
          }
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
  }

  G.BenflixStPatricks = { LOOP, season, variant, greeting, countdown, build, play, LAYOUTS };
})(typeof window !== "undefined" ? window : globalThis);
