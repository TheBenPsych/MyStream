/* Benflix Halloween: the Who's watching room (approved 2026-11-17, thread "Design a Halloween theme for Benflix").
 *
 * A dim plum room lit by a string of little pumpkin lights. Bottom left is an old wooden side table with a big
 * carved jack-o'-lantern, a smaller smiling one and three dripping candles; on the floor another jack-o'-lantern,
 * an iron cauldron of wrapped sweets and a black cat (tail swish, blinking green eyes, head turn). A spider lets
 * itself down from a cobweb under the table. Through the window: a moonlit street with falling leaves, bats over
 * the moon, lit pumpkins on doorsteps and trick-or-treaters with orange candy pails. 1 to 31 October.
 *
 * The pumpkins get carved as October goes on (stages): whole 1 to 10 Oct, two carved 11 to 17 Oct, all carved
 * from 18 Oct. On 31 Oct after 5 PM ("night") the greeting becomes "Happy Halloween!" and bats flit across the
 * top of the room.
 *
 * Like xmas.js and feast.js, the same drawing is used live on the web and by tools/halloween/record.js, which
 * records it as looping videos for the TV and phone app. Everything is a function of the time t (seconds) and
 * repeats every LOOP seconds, so a video of one loop joins up with no jump.
 * The drawing code comes straight from the approved design sheets (halloween-1-look.html, halloween-2-whos-watching.html).
 */
(function (G) {
  "use strict";
  const LOOP = 30;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;

  // ---------- dates ----------
  /** 1 to 31 October (Thanksgiving starts on 16 November) */
  const season = (d) => d.getMonth() === 9;
  /** Halloween night: 31 October from 5 PM */
  const isNight = (d) => d.getMonth() === 9 && d.getDate() === 31 && d.getHours() >= 17;
  /** Which room: "whole" pumpkins 1 to 10 Oct, "half" carved 11 to 17, "carved" from 18, "night" on 31 Oct after 5 PM */
  const stage = (d) => (isNight(d) ? "night" : d.getDate() <= 10 ? "whole" : d.getDate() <= 17 ? "half" : "carved");
  /** The big words: "Happy Halloween", and "Happy Halloween!" on Halloween night */
  const greeting = (d) => (isNight(d) ? "Happy Halloween!" : "Happy Halloween");
  /** The countdown line under the greeting ("14 days to Halloween"), or "" on Halloween night */
  const countdown = (d) => {
    if (!season(d) || isNight(d)) return "";
    const days = 31 - d.getDate();
    return days === 0 ? "Halloween is today!" : days === 1 ? "Halloween is tomorrow" : `${days} days to Halloween`;
  };
  /** Home's line under "Happy Halloween, Ben": the countdown, then "Happy Halloween!" on the 31st */
  const homeLine = (d) => (!season(d) ? "" : d.getDate() === 31 ? "Happy Halloween!" : countdown(d));

  // ---------- drawing kit (from the approved design sheets) ----------
  let UID=0;const u=p=>p+(UID++)+'_';
  const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
  const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
  const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
  const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
  const f1=v=>(+v).toFixed(1);
  const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
  const sh=(id,s)=>`<filter id="${id}" x="-40%" y="-40%" width="180%" height="180%"><feDropShadow dx="0" dy="${f1(s*.6)}" stdDeviation="${s}" flood-color="#000" flood-opacity=".5"/></filter>`;
  const GDEF=`<defs><filter id="Gts" x="-10%" y="-40%" width="120%" height="180%"><feDropShadow dx="0" dy="2" stdDeviation="2.5" flood-color="#000" flood-opacity=".55"/></filter></defs>`;
  const T=(x,y,s,txt,o={})=>`<text x="${x}" y="${y}" font-family="${o.font||'Inter,sans-serif'}" font-weight="${o.w||600}" font-size="${s}" fill="${o.fill||'#F5F0EA'}" ${o.anchor?`text-anchor="${o.anchor}"`:''} ${o.sh?'filter="url(#Gts)"':''} ${o.op?`opacity="${o.op}"`:''}>${txt}</text>`;
  function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
  const pts=a=>a.map((q,i)=>(i?'L':'M')+f1(q[0])+' '+f1(q[1])).join('')+'Z';
  const DOORS=[
   {k:'movies',name:'Movies',sub:'12 new this week',c:'#FFB547',ico:'M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3Z M6.2 5.3l3.1 3.9 M12.4 3.4l3.1 4 M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z'},
   {k:'shows',name:'Shows',sub:'3 new episodes',c:'#7AA7FF',ico:'M4 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z M7 2l5 5 5-5'},
   {k:'live',name:'Live TV',sub:'Monster marathon at 9',c:'#3DD6C6',ico:'M4.9 19.1C1 15.2 1 8.8 4.9 4.9 M7.8 16.2c-2.3-2.3-2.3-6.1 0-8.5 M16.2 7.8c2.3 2.3 2.3 6.1 0 8.5 M19.1 4.9C23 8.8 23 15.1 19.1 19 M14 12a2 2 0 1 1-4 0 2 2 0 0 1 4 0z'},
   {k:'games',name:'Games',sub:'Pick up where you left',c:'#FF6F91',ico:'M6 11h4 M8 9v4 M15 12h.01 M18 10h.01 M17.32 5H6.68a4 4 0 0 0-3.978 3.59C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258A4 4 0 0 0 17.32 5z'},
   {k:'music',name:'Music',sub:'Spooky mixes ready',c:'#B69CFF',ico:'M9 18V5l12-2v13 M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z M21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z'},
   {k:'books',name:'Books',sub:'Ghost stories',c:'#B9E36A',ico:'M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20'}];
  const DW=240,DH=220,DR=20;
  const glint=(x,y,s,op=1)=>`<path d="M${f1(x)} ${f1(y-s)}Q${f1(x+s*.14)} ${f1(y-s*.14)} ${f1(x+s)} ${f1(y)}Q${f1(x+s*.14)} ${f1(y+s*.14)} ${f1(x)} ${f1(y+s)}Q${f1(x-s*.14)} ${f1(y+s*.14)} ${f1(x-s)} ${f1(y)}Q${f1(x-s*.14)} ${f1(y-s*.14)} ${f1(x)} ${f1(y-s)}Z" fill="#fff" opacity="${op}"/>`;

  /* ---------- shared bits ---------- */
  /* an old iron nail head, what things hang from */
  function nail(cx,cy,r){const p=u('nl');return `<defs><radialGradient id="${p}" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#D8D8DE"/><stop offset=".5" stop-color="#7A7A84"/><stop offset="1" stop-color="#2A2A30"/></radialGradient></defs><circle cx="${cx}" cy="${f1(cy+r*.4)}" r="${r}" fill="#000" opacity=".35"/><circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#${p})" stroke="#1A1A1E" stroke-width="1"/><circle cx="${f1(cx-r*.3)}" cy="${f1(cy-r*.3)}" r="${f1(r*.3)}" fill="#fff" opacity=".6"/>`}
  const PUMP=['#FFC266','#F27F1F','#A3420B'];
  /* jack-o'-lantern: five ribbed lobes, stem, carved face lit from inside. face: 'spooky' or 'smile' */
  function pumpkin(cx,cy,w,h,face='spooky',lit=true){const p=u('pk');
   let s=`<defs><radialGradient id="${p}o" cx=".4" cy=".35" r=".75"><stop offset="0" stop-color="${PUMP[0]}"/><stop offset=".55" stop-color="${PUMP[1]}"/><stop offset="1" stop-color="${PUMP[2]}"/></radialGradient>
   <radialGradient id="${p}c" cx=".5" cy=".5" r=".6"><stop offset="0" stop-color="#FFF8C8"/><stop offset=".6" stop-color="#FFD04A"/><stop offset="1" stop-color="#F08A10"/></radialGradient>${blur(p+'b',3.2)}${sh(p+'s',3.5)}</defs><g filter="url(#${p}s)">`;
   const L=[[-.32,.24,.86],[.32,.24,.86],[-.17,.27,.96],[.17,.27,.96],[0,.28,1]];
   L.forEach(([ox,rx,ry])=>{s+=`<ellipse cx="${f1(cx+ox*w)}" cy="${cy}" rx="${f1(rx*w)}" ry="${f1(ry*h/2)}" fill="url(#${p}o)" stroke="#7A300A" stroke-width="1.1"/>`});
   s+=`</g>`;
   [-.24,-.08,.08,.24].forEach(ox=>{s+=`<path d="M${f1(cx+ox*w)} ${f1(cy-h*.44)}Q${f1(cx+ox*w*1.25)} ${cy} ${f1(cx+ox*w)} ${f1(cy+h*.44)}" stroke="#B8500F" stroke-width="1.4" fill="none" opacity=".55"/>`});
   s+=`<ellipse cx="${f1(cx-w*.2)}" cy="${f1(cy-h*.22)}" rx="${f1(w*.07)}" ry="${f1(h*.14)}" fill="#fff" opacity=".35" transform="rotate(-18 ${f1(cx-w*.2)} ${f1(cy-h*.22)})"/>`;
   /* stem */
   s+=`<path d="M${f1(cx-w*.05)} ${f1(cy-h*.44)}C${f1(cx-w*.06)} ${f1(cy-h*.62)} ${f1(cx+w*.02)} ${f1(cy-h*.7)} ${f1(cx+w*.1)} ${f1(cy-h*.74)}L${f1(cx+w*.12)} ${f1(cy-h*.66)}C${f1(cx+w*.06)} ${f1(cy-h*.62)} ${f1(cx+w*.05)} ${f1(cy-h*.54)} ${f1(cx+w*.06)} ${f1(cy-h*.44)}Z" fill="#5E6B2A" stroke="#33391A" stroke-width="1"/>`;
   let F='';const X=v=>f1(cx+v*w),Y=v=>f1(cy+v*h);
   if(face==='spooky'){
    F=`<path d="M${X(-.3)} ${Y(-.08)}L${X(-.1)} ${Y(-.12)}L${X(-.17)} ${Y(-.3)}Z"/><path d="M${X(.3)} ${Y(-.08)}L${X(.1)} ${Y(-.12)}L${X(.17)} ${Y(-.3)}Z"/><path d="M${X(-.04)} ${Y(.02)}L${X(.04)} ${Y(.02)}L${X(0)} ${Y(-.07)}Z"/>
    <path d="M${X(-.33)} ${Y(.1)}Q${X(0)} ${Y(.2)} ${X(.33)} ${Y(.1)}Q${X(.28)} ${Y(.3)} ${X(0)} ${Y(.33)}Q${X(-.28)} ${Y(.3)} ${X(-.33)} ${Y(.1)}Z"/>`;
   }else if(face==='smile'){
    F=`<ellipse cx="${X(-.15)}" cy="${Y(-.12)}" rx="${f1(w*.07)}" ry="${f1(h*.1)}"/><ellipse cx="${X(.15)}" cy="${Y(-.12)}" rx="${f1(w*.07)}" ry="${f1(h*.1)}"/>
    <path d="M${X(-.26)} ${Y(.08)}Q${X(0)} ${Y(.36)} ${X(.26)} ${Y(.08)}Q${X(0)} ${Y(.18)} ${X(-.26)} ${Y(.08)}Z"/>`;}
   s+=`<g fill="#2A0E02">${F}</g>`;
   if(lit)s+=`<g fill="#FFC23A" filter="url(#${p}b)" opacity=".9">${F}</g><g fill="url(#${p}c)" transform="translate(0 1.2)">${F}</g>`;
   /* spooky mouth teeth: two pumpkin flesh teeth left in */
   if(face==='spooky')s+=`<path d="M${X(-.14)} ${Y(.15)}h${f1(w*.07)}v${f1(h*.08)}h${f1(-w*.07)}Z M${X(.07)} ${Y(.15)}h${f1(w*.07)}v${f1(h*.08)}h${f1(-w*.07)}Z M${X(-.04)} ${Y(.33)}h${f1(w*.08)}v${f1(-h*.08)}h${f1(-w*.08)}Z" fill="${PUMP[1]}" stroke="#7A300A" stroke-width=".8"/>`;
   if(face==='smile')s+=`<circle cx="${X(-.28)}" cy="${Y(.06)}" r="${f1(w*.05)}" fill="#FF6A6A" opacity=".55"/><circle cx="${X(.28)}" cy="${Y(.06)}" r="${f1(w*.05)}" fill="#FF6A6A" opacity=".55"/>`;
   return s}
  function lanternCharm(face='spooky'){const cx=184,cy=94,w=100,h=76;const lx=cx-w*.46,rx=cx+w*.46,ay=cy-h*.12;
   return nail(cx,12,6)+`<path d="M${f1(lx)} ${f1(ay)}C${f1(lx)} -6 ${f1(rx)} -6 ${f1(rx)} ${f1(ay)}" stroke="#2A2A30" stroke-width="2.6" fill="none"/><path d="M${f1(lx)} ${f1(ay)}C${f1(lx)} -6 ${f1(rx)} -6 ${f1(rx)} ${f1(ay)}" stroke="#9A9AA6" stroke-width="1" fill="none" opacity=".6"/>`
   +pumpkin(cx,cy,w,h,face)+`<circle cx="${f1(lx)}" cy="${f1(ay)}" r="3" fill="#3A3A40"/><circle cx="${f1(rx)}" cy="${f1(ay)}" r="3" fill="#3A3A40"/>`}

  /* bat hanging upside down, its feet hooked over the top edge, wings wrapped round it */
  function hangingBat(cx,top,s=1,cute=false){const p=u('bt');const X=v=>f1(cx+v*s),Y=v=>f1(top+v*s);
   let o=`<defs><linearGradient id="${p}w" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#3A3146"/><stop offset=".5" stop-color="#1C1724"/><stop offset="1" stop-color="#2E2638"/></linearGradient>${sh(p+'s',3)}</defs>`;
   /* toes hooked over the edge */
   o+=`<path d="M${X(-7)} ${Y(9)}L${X(-6)} ${Y(1)}Q${X(-6)} ${Y(-2.5)} ${X(-9.5)} ${Y(-1.5)} M${X(-6)} ${Y(1)}Q${X(-4)} ${Y(-2.5)} ${X(-2)} ${Y(-1)} M${X(7)} ${Y(9)}L${X(6)} ${Y(1)}Q${X(6)} ${Y(-2.5)} ${X(9.5)} ${Y(-1.5)} M${X(6)} ${Y(1)}Q${X(4)} ${Y(-2.5)} ${X(2)} ${Y(-1)}" stroke="#1A1520" stroke-width="${f1(2.4*s)}" fill="none" stroke-linecap="round"/>`;
   o+=`<g filter="url(#${p}s)">`;
   /* folded wings, wide at the shoulders, scalloped where the finger bones end */
   o+=`<path d="M${X(-6)} ${Y(8)}C${X(-26)} ${Y(14)} ${X(-34)} ${Y(36)} ${X(-30)} ${Y(58)}Q${X(-24)} ${Y(56)} ${X(-22)} ${Y(64)}Q${X(-15)} ${Y(62)} ${X(-12)} ${Y(72)}L${X(0)} ${Y(76)}L${X(12)} ${Y(72)}Q${X(15)} ${Y(62)} ${X(22)} ${Y(64)}Q${X(24)} ${Y(56)} ${X(30)} ${Y(58)}C${X(34)} ${Y(36)} ${X(26)} ${Y(14)} ${X(6)} ${Y(8)}Z" fill="url(#${p}w)" stroke="#0E0B12" stroke-width="1.2"/>`;
   o+=`<path d="M${X(-5)} ${Y(12)}Q${X(-22)} ${Y(30)} ${X(-22)} ${Y(62)} M${X(5)} ${Y(12)}Q${X(22)} ${Y(30)} ${X(22)} ${Y(62)} M${X(-4)} ${Y(14)}Q${X(-10)} ${Y(40)} ${X(-12)} ${Y(70)} M${X(4)} ${Y(14)}Q${X(10)} ${Y(40)} ${X(12)} ${Y(70)}" stroke="#4A4058" stroke-width="${f1(1.4*s)}" fill="none"/>`;
   /* head, upside down: snout up toward the body, ears pointing down */
   const hy=top+86*s;
   o+=`<path d="M${X(-13)} ${f1(hy+6*s)}L${X(-15)} ${f1(hy+24*s)}L${X(-5)} ${f1(hy+11*s)}Z M${X(13)} ${f1(hy+6*s)}L${X(15)} ${f1(hy+24*s)}L${X(5)} ${f1(hy+11*s)}Z" fill="#241D2C" stroke="#0E0B12"/>`;
   o+=`<ellipse cx="${cx}" cy="${f1(hy)}" rx="${f1(14*s)}" ry="${f1(13*s)}" fill="#2A2232" stroke="#0E0B12" stroke-width="1"/>`;
   o+=`<path d="M${X(-13)} ${f1(hy+6*s)}L${X(-14)} ${f1(hy+19*s)}L${X(-8)} ${f1(hy+10*s)}Z M${X(13)} ${f1(hy+6*s)}L${X(14)} ${f1(hy+19*s)}L${X(8)} ${f1(hy+10*s)}Z" fill="#7A5A6A" opacity=".7"/>`;
   if(cute){o+=`<circle cx="${X(-5.5)}" cy="${f1(hy+2*s)}" r="${f1(4.6*s)}" fill="#fff"/><circle cx="${X(5.5)}" cy="${f1(hy+2*s)}" r="${f1(4.6*s)}" fill="#fff"/><circle cx="${X(-5)}" cy="${f1(hy+2.8*s)}" r="${f1(2.4*s)}" fill="#1A1420"/><circle cx="${X(6)}" cy="${f1(hy+2.8*s)}" r="${f1(2.4*s)}" fill="#1A1420"/><circle cx="${X(-4)}" cy="${f1(hy+1.5*s)}" r="${f1(.9*s)}" fill="#fff"/><circle cx="${X(7)}" cy="${f1(hy+1.5*s)}" r="${f1(.9*s)}" fill="#fff"/>
    <path d="M${X(-4)} ${f1(hy-6*s)}Q${cx} ${f1(hy-10*s)} ${X(4)} ${f1(hy-6*s)}" stroke="#fff" stroke-width="${f1(1.4*s)}" fill="none" stroke-linecap="round"/><circle cx="${X(-10)}" cy="${f1(hy-3*s)}" r="${f1(2.4*s)}" fill="#FF8FB0" opacity=".7"/><circle cx="${X(10)}" cy="${f1(hy-3*s)}" r="${f1(2.4*s)}" fill="#FF8FB0" opacity=".7"/>`}
   else{o+=`<circle cx="${X(-5)}" cy="${f1(hy+2*s)}" r="${f1(2.6*s)}" fill="#FFB020"/><circle cx="${X(5)}" cy="${f1(hy+2*s)}" r="${f1(2.6*s)}" fill="#FFB020"/><circle cx="${X(-5)}" cy="${f1(hy+2*s)}" r="${f1(5*s)}" fill="#FFB020" opacity=".18"/><circle cx="${X(5)}" cy="${f1(hy+2*s)}" r="${f1(5*s)}" fill="#FFB020" opacity=".18"/>
    <path d="M${X(-3)} ${f1(hy-8*s)}L${X(-2)} ${f1(hy-12.5*s)}L${X(-1)} ${f1(hy-8*s)}Z M${X(1)} ${f1(hy-8*s)}L${X(2)} ${f1(hy-12.5*s)}L${X(3)} ${f1(hy-8*s)}Z" fill="#fff"/>`}
   o+=`<path d="M${X(-3)} ${f1(hy-11*s)}Q${cx} ${f1(hy-15*s)} ${X(3)} ${f1(hy-11*s)}" fill="#3A3042"/>`;
   return o+`</g>`}
  function batCharm(cute=false){return hangingBat(186,0,1.18,cute)}

  /* cobweb anchored in a corner (x,y) opening toward dirX, dirY */
  function cobweb(x,y,len,ang0,ang1,n=7,rings=6){let s='';const A=[];for(let i=0;i<n;i++)A.push(ang0+(ang1-ang0)*i/(n-1));
   const P=(a,r)=>[x+Math.cos(a)*r,y+Math.sin(a)*r];
   A.forEach(a=>{const q=P(a,len);s+=`<path d="M${x} ${y}L${f1(q[0])} ${f1(q[1])}" />`});
   for(let k=1;k<=rings;k++){const r=len*k/(rings+.4);for(let i=0;i<n-1;i++){const a=P(A[i],r),b=P(A[i+1],r),m=P((A[i]+A[i+1])/2,r*.86);s+=`<path d="M${f1(a[0])} ${f1(a[1])}Q${f1(m[0])} ${f1(m[1])} ${f1(b[0])} ${f1(b[1])}" fill="none"/>`}}
   return `<g stroke="#000" stroke-opacity=".25" stroke-width="3" fill="none">${s}</g><g stroke="#FFFFFF" stroke-opacity=".88" stroke-width="1.4" fill="none">${s}</g>`}
  /* a spider hanging on its silk: spinnerets up, head down */
  function spider(cx,cy,s=1){const p=u('sp');const X=v=>f1(cx+v*s),Y=v=>f1(cy+v*s);
   let o=`<defs><radialGradient id="${p}" cx=".38" cy=".32" r=".75"><stop offset="0" stop-color="#5A4A62"/><stop offset=".5" stop-color="#1E1822"/><stop offset="1" stop-color="#08060A"/></radialGradient>${sh(p+'s',2.5)}</defs><g filter="url(#${p}s)">`;
   const legs=[[-1,-6,-22,-14,-30,0],[-1,-3,-24,-2,-30,14],[-1,1,-22,10,-26,26],[-1,4,-16,18,-18,34]];
   legs.forEach(l=>{[-1,1].forEach(sg=>{o+=`<path d="M${X(sg*4)} ${Y(14+l[1])}L${X(sg*-l[2])} ${Y(14+l[3])}L${X(sg*-l[4])} ${Y(14+l[5])}" stroke="#120E14" stroke-width="${f1(2.8*s)}" fill="none" stroke-linecap="round" stroke-linejoin="round"/>`})});
   o+=`<ellipse cx="${cx}" cy="${Y(0)}" rx="${f1(12*s)}" ry="${f1(14*s)}" fill="url(#${p})" stroke="#000" stroke-width="1"/>`;
   o+=`<path d="M${X(-4)} ${Y(-6)}L${X(0)} ${Y(-2)}L${X(4)} ${Y(-6)} M${X(-5)} ${Y(2)}L${X(0)} ${Y(6)}L${X(5)} ${Y(2)}" stroke="#E04A2A" stroke-width="${f1(1.6*s)}" fill="none" opacity=".85"/>`;
   o+=`<circle cx="${cx}" cy="${Y(17)}" r="${f1(7.5*s)}" fill="url(#${p})" stroke="#000" stroke-width="1"/>`;
   o+=`<circle cx="${X(-2.6)}" cy="${Y(20)}" r="${f1(1.3*s)}" fill="#FFB0A0"/><circle cx="${X(2.6)}" cy="${Y(20)}" r="${f1(1.3*s)}" fill="#FFB0A0"/>`;
   o+=`<ellipse cx="${X(-4)}" cy="${Y(-6)}" rx="${f1(3*s)}" ry="${f1(4.5*s)}" fill="#fff" opacity=".35"/>`;
   return o+`</g>`}
  function spiderCharm(){const p=u('sw');return `<defs><clipPath id="${p}"><rect width="${DW}" height="${DH}" rx="${DR}"/></clipPath></defs><g clip-path="url(#${p})">`+cobweb(DW,0,118,Math.PI*.5,Math.PI,7,6)+`</g><path d="M190 24V92" stroke="#fff" stroke-width="1.3" opacity=".9"/>`+spider(190,106,1.25)}

  /* witch's hat hanging from a loop at its tip */
  function witchHat(cx,by,s=1){const p=u('wh');const X=v=>f1(cx+v*s),Y=v=>f1(by+v*s);
   let o=`<defs><linearGradient id="${p}c" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#4A3A5C"/><stop offset=".45" stop-color="#241A30"/><stop offset="1" stop-color="#0E0A14"/></linearGradient><linearGradient id="${p}g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFE7A0"/><stop offset=".5" stop-color="#E0A93A"/><stop offset="1" stop-color="#8A5E14"/></linearGradient>${sh(p+'s',4)}</defs><g filter="url(#${p}s)">`;
   o+=`<ellipse cx="${cx}" cy="${Y(0)}" rx="${f1(60*s)}" ry="${f1(13*s)}" fill="#160F1E" stroke="#05030A" stroke-width="1.2"/>`;
   o+=`<ellipse cx="${cx}" cy="${Y(-2)}" rx="${f1(56*s)}" ry="${f1(10*s)}" fill="none" stroke="#3E3050" stroke-width="1.4"/>`;
   o+=`<path d="M${X(-30)} ${Y(-2)}C${X(-24)} ${Y(-34)} ${X(-14)} ${Y(-58)} ${X(-2)} ${Y(-74)}C${X(4)} ${Y(-82)} ${X(12)} ${Y(-90)} ${X(22)} ${Y(-92)}C${X(16)} ${Y(-84)} ${X(10)} ${Y(-76)} ${X(10)} ${Y(-66)}C${X(14)} ${Y(-44)} ${X(22)} ${Y(-24)} ${X(30)} ${Y(-2)}Q${cx} ${Y(6)} ${X(-30)} ${Y(-2)}Z" fill="url(#${p}c)" stroke="#05030A" stroke-width="1.2"/>`;
   o+=`<path d="M${X(-27)} ${Y(-10)}Q${cx} ${Y(-2)} ${X(27)} ${Y(-10)}L${X(25)} ${Y(-22)}Q${cx} ${Y(-14)} ${X(-24)} ${Y(-22)}Z" fill="#6B2E9A" stroke="#2A0E40" stroke-width="1"/>`;
   o+=`<rect x="${X(-8)}" y="${Y(-23)}" width="${f1(16*s)}" height="${f1(14*s)}" rx="2" fill="none" stroke="url(#${p}g)" stroke-width="${f1(3.2*s)}"/>`;
   o+=`<path d="M${X(-18)} ${Y(-40)}Q${X(-10)} ${Y(-62)} ${X(0)} ${Y(-72)}" stroke="#fff" stroke-width="${f1(2.4*s)}" fill="none" opacity=".22" stroke-linecap="round"/>`;
   return o+`</g>`}
  function hatCharm(){const cx=174,by=128;return nail(198,10,6)+`<path d="M198 14Q202 22 ${cx+22*1.05} ${by-92*1.05+2}" stroke="#2A1A0A" stroke-width="2" fill="none"/>`+witchHat(cx,by,1.05)}

  /* cheesecloth ghost tied on a string */
  function ghost(cx,cy,s=1,cute=false){const p=u('gh');const X=v=>f1(cx+v*s),Y=v=>f1(cy+v*s);
   let o=`<defs><linearGradient id="${p}" x1="0" y1="0" x2=".4" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset=".6" stop-color="#ECE8F4"/><stop offset="1" stop-color="#BDB6CE"/></linearGradient>${sh(p+'s',4)}</defs><g filter="url(#${p}s)">`;
   o+=`<path d="M${X(-26)} ${Y(0)}C${X(-26)} ${Y(-34)} ${X(26)} ${Y(-34)} ${X(26)} ${Y(0)}C${X(28)} ${Y(28)} ${X(36)} ${Y(52)} ${X(44)} ${Y(72)}Q${X(34)} ${Y(66)} ${X(26)} ${Y(76)}Q${X(16)} ${Y(64)} ${X(6)} ${Y(78)}Q${X(-4)} ${Y(66)} ${X(-14)} ${Y(76)}Q${X(-24)} ${Y(68)} ${X(-32)} ${Y(74)}C${X(-26)} ${Y(52)} ${X(-26)} ${Y(28)} ${X(-26)} ${Y(0)}Z" fill="url(#${p})" stroke="#9A92AE" stroke-width="1"/>`;
   o+=`<path d="M${X(-12)} ${Y(30)}Q${X(-10)} ${Y(52)} ${X(-14)} ${Y(70)} M${X(10)} ${Y(30)}Q${X(16)} ${Y(52)} ${X(22)} ${Y(70)} M${X(18)} ${Y(24)}Q${X(30)} ${Y(46)} ${X(36)} ${Y(64)}" stroke="#A8A0BC" stroke-width="${f1(1.4*s)}" fill="none" opacity=".7"/>`;
   o+=`<path d="M${X(-24)} ${Y(22)}Q${cx} ${Y(30)} ${X(24)} ${Y(22)}" stroke="#7A6A50" stroke-width="${f1(2*s)}" fill="none"/>`;
   if(cute){o+=`<ellipse cx="${X(-9)}" cy="${Y(-2)}" rx="${f1(4*s)}" ry="${f1(5.4*s)}" fill="#1E1A28"/><ellipse cx="${X(9)}" cy="${Y(-2)}" rx="${f1(4*s)}" ry="${f1(5.4*s)}" fill="#1E1A28"/><circle cx="${X(-8)}" cy="${Y(-4)}" r="${f1(1.4*s)}" fill="#fff"/><circle cx="${X(10)}" cy="${Y(-4)}" r="${f1(1.4*s)}" fill="#fff"/><path d="M${X(-6)} ${Y(9)}Q${cx} ${Y(15)} ${X(6)} ${Y(9)}" stroke="#1E1A28" stroke-width="${f1(2*s)}" fill="none" stroke-linecap="round"/><circle cx="${X(-16)}" cy="${Y(7)}" r="${f1(3.4*s)}" fill="#FF9AB8" opacity=".6"/><circle cx="${X(16)}" cy="${Y(7)}" r="${f1(3.4*s)}" fill="#FF9AB8" opacity=".6"/>`}
   else{o+=`<ellipse cx="${X(-9)}" cy="${Y(-3)}" rx="${f1(4.6*s)}" ry="${f1(7*s)}" fill="#1E1A28"/><ellipse cx="${X(9)}" cy="${Y(-3)}" rx="${f1(4.6*s)}" ry="${f1(7*s)}" fill="#1E1A28"/><ellipse cx="${cx}" cy="${Y(12)}" rx="${f1(4*s)}" ry="${f1(5.6*s)}" fill="#1E1A28"/>`}
   o+=`<ellipse cx="${X(-12)}" cy="${Y(-16)}" rx="${f1(6*s)}" ry="${f1(4*s)}" fill="#fff" opacity=".9"/>`;
   return o+`</g>`}
  function ghostCharm(cute=false){return nail(186,8,6)+`<path d="M186 12V${f1(52-27*1.15)}" stroke="#7A6A50" stroke-width="2"/>`+ghost(186,56,1.15,cute)}

  /* an old brass skeleton key on a purple ribbon */
  function keyCharm(){const p=u('ky');const cx=186;
   let s=`<defs><linearGradient id="${p}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFE3A0"/><stop offset=".45" stop-color="#C8902E"/><stop offset="1" stop-color="#6A4410"/></linearGradient>${sh(p+'s',3.5)}</defs>`;
   s+=nail(cx,8,6)+`<path d="M${cx-3} 12L${cx-6} 42M${cx+3} 12L${cx+6} 42" stroke="#5A2E8A" stroke-width="6" stroke-linecap="round"/>`;
   s+=`<g filter="url(#${p}s)">`;
   /* the bow: three rings like a clover */
   [[0,-14],[-13,6],[13,6]].forEach(([dx,dy])=>{s+=`<circle cx="${cx+dx}" cy="${62+dy}" r="13" fill="none" stroke="url(#${p})" stroke-width="7"/>`});
   s+=`<circle cx="${cx}" cy="62" r="8" fill="url(#${p})" stroke="#5A3A0C"/>`;
   s+=`<rect x="${cx-4.5}" y="74" width="9" height="74" rx="3" fill="url(#${p})" stroke="#5A3A0C" stroke-width="1"/>`;
   s+=`<rect x="${cx-8}" y="80" width="16" height="7" rx="3" fill="url(#${p})" stroke="#5A3A0C"/><rect x="${cx-7}" y="92" width="14" height="5" rx="2.5" fill="url(#${p})" stroke="#5A3A0C"/>`;
   s+=`<path d="M${cx+4} 128H${cx+26}V136H${cx+20}V140H${cx+26}V150H${cx+14}V144H${cx+4}Z" fill="url(#${p})" stroke="#5A3A0C" stroke-width="1"/>`;
   s+=`</g><path d="M${cx-1.5} 78V144" stroke="#FFF1C2" stroke-width="1.6" opacity=".7"/>`;
   /* ribbon threaded through the top ring, the bow sits at the nail */
   s+=`<path d="M${cx-6} 42Q${cx} 52 ${cx+6} 42" stroke="#5A2E8A" stroke-width="6" fill="none" stroke-linecap="round"/>`;
   s+=`<path d="M${cx} 14C${cx-24} 2 ${cx-28} 26 ${cx-4} 18Z M${cx} 14C${cx+24} 2 ${cx+28} 26 ${cx+4} 18Z" fill="#6B3AA0" stroke="#2E1250" stroke-width="1.4"/><circle cx="${cx}" cy="15" r="4.5" fill="#6B3AA0" stroke="#2E1250" stroke-width="1.4"/>`;
   return s+glint(cx+18,58,5,.9)}

  const CHARM={movies:()=>lanternCharm('spooky'),shows:()=>batCharm(false),live:spiderCharm,games:hatCharm,music:()=>ghostCharm(false),books:keyCharm};

  /* ---------- Kids treats ---------- */
  function candy(cx,cy,s,c1,c2){const p=u('cd');const X=v=>f1(cx+v*s),Y=v=>f1(cy+v*s);
   return `<defs><radialGradient id="${p}" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="${lt(c1,.5)}"/><stop offset=".55" stop-color="${c1}"/><stop offset="1" stop-color="${dk(c1,.35)}"/></radialGradient>${sh(p+'s',3)}</defs><g filter="url(#${p}s)">
   <path d="M${X(-20)} ${Y(0)}L${X(-38)} ${Y(-14)}Q${X(-34)} ${Y(0)} ${X(-38)} ${Y(14)}Z M${X(20)} ${Y(0)}L${X(38)} ${Y(-14)}Q${X(34)} ${Y(0)} ${X(38)} ${Y(14)}Z" fill="${lt(c1,.2)}" stroke="${dk(c1,.4)}" stroke-width="1"/>
   <ellipse cx="${cx}" cy="${cy}" rx="${f1(22*s)}" ry="${f1(16*s)}" fill="url(#${p})" stroke="${dk(c1,.4)}" stroke-width="1"/>
   <path d="M${X(-10)} ${Y(-14)}Q${X(-2)} ${Y(0)} ${X(-10)} ${Y(14)} M${X(4)} ${Y(-16)}Q${X(12)} ${Y(0)} ${X(4)} ${Y(16)}" stroke="${c2}" stroke-width="${f1(4*s)}" fill="none"/>
   <ellipse cx="${X(-9)}" cy="${Y(-7)}" rx="${f1(6*s)}" ry="${f1(3*s)}" fill="#fff" opacity=".6"/></g>`}
  function candyCharm(){return nail(178,8,6)+`<path d="M178 12V62" stroke="#fff" stroke-width="1.8"/><path d="M178 62 c-6 -4 -10 2 -4 6" stroke="#fff" stroke-width="1.6" fill="none"/>`+candy(178,84,1.4,'#9B5BE0','#FFE14A')}
  function candyCorn(cx,cy,s,rot){const p=u('cc');const X=v=>f1(cx+v*s),Y=v=>f1(cy+v*s);
   return `<defs><clipPath id="${p}"><path d="M${X(0)} ${Y(-26)}C${X(8)} ${Y(-20)} ${X(18)} ${Y(6)} ${X(18)} ${Y(16)}Q${X(0)} ${Y(26)} ${X(-18)} ${Y(16)}C${X(-18)} ${Y(6)} ${X(-8)} ${Y(-20)} ${X(0)} ${Y(-26)}Z"/></clipPath>${sh(p+'s',2.5)}</defs><g transform="rotate(${rot} ${cx} ${cy})" filter="url(#${p}s)"><g clip-path="url(#${p})">
   <rect x="${X(-20)}" y="${Y(-28)}" width="${f1(40*s)}" height="${f1(18*s)}" fill="#FFF6E0"/><rect x="${X(-20)}" y="${Y(-10)}" width="${f1(40*s)}" height="${f1(15*s)}" fill="#FF8A1E"/><rect x="${X(-20)}" y="${Y(5)}" width="${f1(40*s)}" height="${f1(24*s)}" fill="#FFD23A"/>
   <ellipse cx="${X(-6)}" cy="${Y(-4)}" rx="${f1(4*s)}" ry="${f1(14*s)}" fill="#fff" opacity=".35"/></g></g>`}
  function lollyCharm(){const p=u('lp');const cx=186,cy=96,r=30;let sw='';for(let k=0;k<5;k++){const a0=k*Math.PI*2/5;let d=`M${cx} ${cy}`;for(let t=0;t<=1.0001;t+=.1){const a=a0+t*4.2,rr=r*t;d+=`L${f1(cx+Math.cos(a)*rr)} ${f1(cy+Math.sin(a)*rr)}`}for(let t=1;t>=-.0001;t-=.1){const a=a0+.62+t*4.2,rr=r*t;d+=`L${f1(cx+Math.cos(a)*rr)} ${f1(cy+Math.sin(a)*rr)}`}sw+=`<path d="${d}Z" fill="${k%2?'#FF8A1E':'#8A4AD8'}"/>`}
   return nail(cx,8,6)+`<path d="M${cx} 12V40" stroke="#8A4AD8" stroke-width="3"/><defs>${sh(p,3)}</defs><g filter="url(#${p})"><rect x="${cx-3}" y="36" width="6" height="34" rx="3" fill="#FFF8EC" stroke="#C8B8A0"/><circle cx="${cx}" cy="${cy}" r="${r}" fill="#FFF0D8" stroke="#9A6A3A" stroke-width="1"/>${sw}<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="#fff" stroke-width="2.5" opacity=".5"/></g><path d="M${cx-8} 40c-12 -10 -14 4 -2 2 M${cx+8} 40c12 -10 14 4 2 2" stroke="#8A4AD8" stroke-width="3" fill="none"/><circle cx="${cx}" cy="40" r="4" fill="#8A4AD8"/>`+glint(cx-14,cy-14,7,.9)}
  function cornCharm(){return `<g>`+candyCorn(150,52,1.3,-24)+candyCorn(190,40,1.45,8)+candyCorn(222,64,1.2,32)+`</g>`}
  function moonStar(){const p=u('ms');let s=`<defs><radialGradient id="${p}" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#FFF6C8"/><stop offset=".6" stop-color="#FFD84A"/><stop offset="1" stop-color="#D89A10"/></radialGradient><mask id="${p}m"><rect width="240" height="220" fill="#fff"/><circle cx="202" cy="64" r="34" fill="#000"/></mask>${sh(p+'s',3)}</defs>`;
   s+=nail(186,8,6)+`<path d="M186 12V30" stroke="#fff" stroke-width="1.8"/><path d="M186 112V124" stroke="#fff" stroke-width="1.5" opacity=".9"/>`;
   s+=`<g filter="url(#${p}s)"><circle cx="186" cy="74" r="40" fill="url(#${p})" mask="url(#${p}m)"/></g>`;
   let st='';for(let k=0;k<10;k++){const a=-Math.PI/2+k*Math.PI/5,rr=k%2?6:13;st+=(k?'L':'M')+f1(186+Math.cos(a)*rr)+' '+f1(137+Math.sin(a)*rr)}
   s+=`<path d="${st}Z" fill="url(#${p})" stroke="#B07A0A" stroke-width="1" filter="url(#${p}s)"/>`;
   s+=`<circle cx="168" cy="82" r="3.5" fill="#FF9AB8" opacity=".8"/><path d="M160 70q3 -3 6 0 M172 66q3 -3 6 0" stroke="#5A3A0C" stroke-width="2" fill="none" stroke-linecap="round"/><path d="M162 88q6 5 12 0" stroke="#5A3A0C" stroke-width="2" fill="none" stroke-linecap="round"/>`;
   return s}
  const KIDS={movies:()=>lanternCharm('smile'),shows:()=>batCharm(true),live:()=>ghostCharm(true),games:candyCharm,music:lollyCharm,books:moonStar};


  const NIGHT='#0D0812';
  function batSil(x,y,s,c=NIGHT,rot=0){const R=[[0,-.12],[.07,-.3],[.12,-.12],[.35,-.3],[.65,-.34],[1,-.22],[.86,-.05],[.88,.18],[.72,.04],[.56,.1],[.5,.26],[.38,.08],[.24,.1],[.14,.24],[0,.32]];
   const all=R.concat(R.slice(1,-1).reverse().map(q=>[-q[0],q[1]]));return `<path d="${pts(all.map(q=>[x+q[0]*s,y+q[1]*s]))}" fill="${c}" stroke-linejoin="round" transform="rotate(${rot} ${x} ${y})"/>`}
  function moonDisc(cx,cy,r,col=['#FFF8DA','#F6DE96','#D9B45A'],crescent=0){const p=u('mn');
   let s=`<defs><radialGradient id="${p}" cx=".42" cy=".38" r=".7"><stop offset="0" stop-color="${col[0]}"/><stop offset=".7" stop-color="${col[1]}"/><stop offset="1" stop-color="${col[2]}"/></radialGradient>${blur(p+'b',14)}`;
   if(crescent)s+=`<mask id="${p}m" maskUnits="userSpaceOnUse" x="${cx-r*3}" y="${cy-r*3}" width="${r*6}" height="${r*6}"><rect x="${cx-r*3}" y="${cy-r*3}" width="${r*6}" height="${r*6}" fill="#fff"/><circle cx="${cx+crescent}" cy="${cy-r*.18}" r="${r*.9}" fill="#000"/></mask>`;
   s+=`</defs><circle cx="${cx}" cy="${cy}" r="${r*1.25}" fill="${col[1]}" opacity=".42" filter="url(#${p}b)"/>`;
   s+=`<g ${crescent?`mask="url(#${p}m)"`:''}><circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#${p})"/>`;
   if(!crescent)s+=`<circle cx="${cx-r*.3}" cy="${cy+r*.2}" r="${r*.16}" fill="${col[2]}" opacity=".35"/><circle cx="${cx+r*.25}" cy="${cy-r*.3}" r="${r*.1}" fill="${col[2]}" opacity=".3"/><circle cx="${cx+r*.35}" cy="${cy+r*.35}" r="${r*.12}" fill="${col[2]}" opacity=".28"/>`;
   return s+`</g>`}
  const PURP='#4A2A78',ORNG='#F07A1E';
  function smooth(P,closed){const n=P.length;let d=`M${f1(P[0][0])} ${f1(P[0][1])}`;const g=i=>closed?P[(i+n)%n]:P[Math.max(0,Math.min(n-1,i))];
   for(let i=0;i<(closed?n:n-1);i++){const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);d+=`C${f1(p1[0]+(p2[0]-p0[0])/6)} ${f1(p1[1]+(p2[1]-p0[1])/6)} ${f1(p2[0]-(p3[0]-p1[0])/6)} ${f1(p2[1]-(p3[1]-p1[1])/6)} ${f1(p2[0])} ${f1(p2[1])}`}return d+(closed?'Z':'')}
  function flameD(bx,by,w,h,t,ph,sp=1){const P=[];const N=7;
   for(let i=0;i<=N;i++){const s=i/N;const wob=Math.sin(t*wq(5.2)+ph+s*3.1)*w*.16*s;const hw=w/2*Math.pow(1-s,.85)*(1+.25*Math.sin(s*Math.PI));P.push([bx-hw+wob,by-s*h])}
   const tipx=bx+Math.sin(t*wq(4.1)+ph)*w*.2,tipy=by-h*(1.04+.08*Math.sin(t*wq(6.3)+ph*2));
   const R=[];for(let i=N;i>=0;i--){const s=i/N;const wob=Math.sin(t*wq(5.2)+ph+s*3.1)*w*.16*s;const hw=w/2*Math.pow(1-s,.85)*(1+.25*Math.sin(s*Math.PI));R.push([bx+hw+wob,by-s*h])}
   return smooth([...P.slice(0,N),[tipx,tipy],...R.slice(1)],true)}
  let RID=0;
  function groundY(x,ww,wh){return wh-40+Math.sin(x*.02)*3}
  /* ---------- the room: deep plum walls, a string of little pumpkin lights ---------- */
  function pumpkinBulb(x,y,r,k,gid){return `<circle class="bulbh" data-k="${k}" cx="${f1(x)}" cy="${f1(y+r*1.4)}" r="${f1(r*3.4)}" fill="url(#${gid})"/><rect x="${f1(x-r*.3)}" y="${f1(y)}" width="${f1(r*.6)}" height="${f1(r*.5)}" fill="#2A2418"/>
   ${[-.5,0,.5].map(o=>`<ellipse cx="${f1(x+o*r*.9)}" cy="${f1(y+r*1.45)}" rx="${f1(r*.62)}" ry="${f1(r*.9)}" fill="#FF9A3A" stroke="#C85A10" stroke-width=".8"/>`).join('')}
   <path d="M${f1(x-r*.45)} ${f1(y+r*1.25)}l${f1(r*.2)} ${f1(-r*.3)}l${f1(r*.2)} ${f1(r*.3)}Z M${f1(x+r*.05)} ${f1(y+r*1.25)}l${f1(r*.2)} ${f1(-r*.3)}l${f1(r*.2)} ${f1(r*.3)}Z M${f1(x-r*.4)} ${f1(y+r*1.7)}q${f1(r*.4)} ${f1(r*.35)} ${f1(r*.8)} 0" fill="#FFF0A0" stroke="#FFF0A0" stroke-width="${f1(r*.08)}"/>`}
  function room(W2,H2,win,lights=true){const id='rm'+(RID++);const fl=H2*.8;
   let s=`<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0E0814"/><stop offset="1" stop-color="#1C1226"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#22160F"/><stop offset="1" stop-color="#0A0706"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".62"/></radialGradient><radialGradient id="${id}h"><stop offset="0" stop-color="#FFA040" stop-opacity=".8"/><stop offset=".35" stop-color="#FF8A20" stop-opacity=".22"/><stop offset="1" stop-color="#FF8A20" stop-opacity="0"/></radialGradient></defs>`;
   s+=`<rect width="${W2}" height="${H2}" fill="url(#${id}w)"/>`;
   /* faint damask-like wallpaper stripes */
   for(let x=0;x<W2;x+=W2/24)s+=`<rect x="${f1(x)}" y="0" width="${f1(W2/48)}" height="${fl}" fill="#fff" opacity=".013"/>`;
   s+=`<rect y="${f1(fl-H2*.16)}" width="${W2}" height="${f1(H2*.16)}" fill="#000" opacity=".22"/><rect y="${f1(fl-H2*.16)}" width="${W2}" height="4" fill="#3A2A44" opacity=".8"/>`;
   if(win)s+=windowSVG(win);
   s+=`<rect y="${fl}" width="${W2}" height="${H2-fl}" fill="url(#${id}f)"/>`;for(let k=1;k<6;k++)s+=`<path d="M0 ${f1(fl+(H2-fl)*k/6)} H${W2}" stroke="#000" stroke-opacity=".25" stroke-width="2"/>`;
   s+=`<rect y="${fl-10}" width="${W2}" height="10" fill="#120C16"/>`;
   if(lights){const sc=W2/1920;const swags=W2<600?[[0,22,W2,22,22]]:[[300*sc,36*sc,800*sc,36*sc,96*sc],[800*sc,36*sc,1300*sc,36*sc,96*sc]];
    swags.forEach(([x1,y1,x2,y2,sag])=>{const N=Math.round((x2-x1)/(W2<600?34:52*sc));const pt=k=>{const t=k/N;return [x1+(x2-x1)*t,y1+(y2-y1)*t+sag*4*t*(1-t)]};
     let wire='';for(let k=0;k<=N;k++){const [x,y]=pt(k);wire+=(k?'L':'M')+f1(x)+' '+f1(y)}s+=`<path d="${wire}" stroke="#141010" stroke-width="${f1(3*Math.max(sc,.5))}" fill="none"/>`;
     for(let k=1;k<N;k++){const [x,y]=pt(k);s+=pumpkinBulb(x,y,W2<600?7:12*sc,k,id+'h')}})}
   return {bg:s,vig:`<rect width="${W2}" height="${H2}" fill="url(#${id}v)"/>`}}

  /* ---------- the window: an autumn street on a moonlit night ---------- */
  function windowSVG([wx,wy,ww,wh]){const id='wn'+(RID++);const r=rnd(31);
   let s=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0A0618"/><stop offset=".7" stop-color="#2A1A48"/><stop offset="1" stop-color="#4A2E5E"/></linearGradient><radialGradient id="${id}l"><stop offset="0" stop-color="#FFC870" stop-opacity=".6"/><stop offset="1" stop-color="#FFC870" stop-opacity="0"/></radialGradient></defs><rect width="${ww}" height="${wh}" fill="url(#${id}s)"/>`;
   for(let k=0;k<22;k++)s+=`<circle cx="${f1(r()*ww)}" cy="${f1(r()*wh*.4)}" r="${f1(.6+r()*.9)}" fill="#fff" opacity="${f1(.3+r()*.5)}"/>`;
   s+=moonDisc(ww*.68,wh*.2,ww*.13);
   /* bats crossing the sky */
   s+=`<g class="wbats">${[0,1,2].map(k=>`<g class="wbat" data-k="${k}">${batSil(0,0,10-k*2,'#0A0612')}</g>`).join('')}</g>`;
   /* houses with lit windows and a glowing pumpkin on each step */
   let x=-10;const houses=[];while(x<ww){const w=50+r()*40,h=wh*(.2+r()*.12),base=wh*.66;const y=base-h;houses.push([x,w,y,base]);x+=w+6+r()*10}
   houses.forEach(([x,w,y,base])=>{s+=`<path d="M${f1(x)} ${f1(base)}V${f1(y)}L${f1(x+w/2)} ${f1(y-w*.42)}L${f1(x+w)} ${f1(y)}V${f1(base)}Z" fill="#100A1A"/>`;
    for(let yy=y+8;yy<base-14;yy+=16)for(let xx=x+7;xx<x+w-10;xx+=14)if(r()<.45)s+=`<rect x="${f1(xx)}" y="${f1(yy)}" width="6" height="8" fill="#FFB860" opacity="${f1(.45+r()*.5)}"/>`;
    const px=x+w*.5;s+=`<circle cx="${f1(px)}" cy="${f1(base-4)}" r="9" fill="#FF9A30" opacity=".25"/><ellipse cx="${f1(px)}" cy="${f1(base-4)}" rx="5" ry="4" fill="#F07A1E"/><path d="M${f1(px-3)} ${f1(base-5)}h2v1.5h-2Z M${f1(px+1)} ${f1(base-5)}h2v1.5h-2Z M${f1(px-2.5)} ${f1(base-2.5)}h5v1h-5Z" fill="#FFE070"/>`});
   /* bare trees */
   [[ww*.08,wh*.7],[ww*.92,wh*.7]].forEach(([tx,ty],k)=>{s+=`<path d="M${f1(tx)} ${f1(ty)}C${f1(tx-2)} ${f1(ty-40)} ${f1(tx+4)} ${f1(ty-70)} ${f1(tx-2)} ${f1(ty-110)}M${f1(tx)} ${f1(ty-60)}Q${f1(tx-20)} ${f1(ty-80)} ${f1(tx-30)} ${f1(ty-78)}M${f1(tx+1)} ${f1(ty-80)}Q${f1(tx+18)} ${f1(ty-96)} ${f1(tx+26)} ${f1(ty-108)}M${f1(tx-1)} ${f1(ty-40)}Q${f1(tx+14)} ${f1(ty-50)} ${f1(tx+22)} ${f1(ty-48)}" stroke="#0A0612" stroke-width="4" fill="none" stroke-linecap="round"/>`});
   /* pavement */
   let g=`M0 ${f1(groundY(0,ww,wh))}`;for(let x=0;x<=ww;x+=10)g+=`L${x} ${f1(groundY(x,ww,wh))}`;g+=`V${wh} H0Z`;
   s+=`<rect y="${f1(wh*.66)}" width="${ww}" height="${f1(wh*.34)}" fill="#1A1424"/><path d="${g}" fill="#4A4258"/><path d="M0 ${f1(wh-22)} H${ww}" stroke="#6A6078" stroke-width="2" opacity=".6"/>`;
   const lx=ww*.82;s+=`<circle cx="${f1(lx)}" cy="${f1(wh-128)}" r="46" fill="url(#${id}l)"/><rect x="${f1(lx-2.5)}" y="${f1(wh-130)}" width="5" height="${f1(90)}" fill="#120C1A"/><path d="M${f1(lx-9)} ${f1(wh-134)} h18 l-3 10 h-12Z" fill="#120C1A"/><rect x="${f1(lx-6)}" y="${f1(wh-128)}" width="12" height="7" fill="#FFD090"/>`;
   /* falling leaves */
   const LC=['#E0702A','#C8401E','#E8A83A','#8A4A1E'];let lv='';for(let k=0;k<18;k++)lv+=`<path class="lf" data-x="${f1(r()*ww)}" data-y="${f1(r()*wh)}" data-v="${f1(14+r()*12)}" data-d="${f1(r()*6)}" data-wh="${wh}" d="M0 -4 Q4 -2 3 2 Q0 5 -3 2 Q-4 -2 0 -4Z" fill="${LC[k%4]}"/>`;
   return `<rect x="${wx-14}" y="${wy-14}" width="${ww+28}" height="${wh+28}" rx="6" fill="#1E1626"/><svg x="${wx}" y="${wy}" width="${ww}" height="${wh}" overflow="hidden">${s}<g class="walkers" data-w="${ww}" data-h="${wh}">${walkers()}</g>${lv}</svg><rect x="${wx+ww/2-4}" y="${wy}" width="8" height="${wh}" fill="#1E1626"/><rect x="${wx}" y="${wy+wh/2-4}" width="${ww}" height="8" fill="#1E1626"/><rect x="${wx-22}" y="${wy+wh+12}" width="${ww+44}" height="10" rx="3" fill="#1E1626"/>`}

  /* ---------- trick-or-treaters (feet on the ground at y=0, facing left) ---------- */
  const skin='#E8B48A',skin2='#B97E58';
  function leg(x,hipY,col,shoe,ph){return `<g class="lg" data-x="${x}" data-p="${hipY}" data-ph="${ph}"><path d="M${x} ${hipY} L${x} -3" stroke="${col}" stroke-width="6" stroke-linecap="round"/><path d="M${x+2} -1 h-8 a3 3 0 0 1 0 -5 h6Z" fill="${shoe}" stroke="${shoe}" stroke-width="2" stroke-linejoin="round"/></g>`}
  function face(x,y,r,hair,sk=skin){return `<circle cx="${x}" cy="${y}" r="${r}" fill="${sk}"/><circle cx="${x-r*.45}" cy="${y-r*.1}" r="${r*.13}" fill="#2A1A10"/><path d="M${x-r*.6} ${y+r*.35} q${r*.25} ${r*.2} ${r*.5} 0" stroke="#7A3A20" stroke-width="1.3" fill="none" stroke-linecap="round"/><circle cx="${x-r*.15}" cy="${y+r*.25}" r="${r*.2}" fill="#E07A6A" opacity=".45"/>`+(hair?`<path d="M${x-r} ${y-r*.1} C${x-r} ${y-r*1.3} ${x+r*1.1} ${y-r*1.4} ${x+r} ${y+r*.2} C${x+r*.6} ${y-r*.3} ${x} ${y-r*.6} ${x-r} ${y-r*.1}Z" fill="${hair}"/>`:'')}
  /* the orange candy pail every trick-or-treater carries */
  const pail=(x,y,s=1)=>`<path d="M${x-7*s} ${y-12*s} Q${x} ${y-24*s} ${x+7*s} ${y-12*s}" stroke="#1A1A1A" stroke-width="1.4" fill="none"/><path d="M${x-8*s} ${y-12*s} h${16*s} l${-1.5*s} ${13*s} h${-13*s}Z" fill="#F07A1E" stroke="#A04A0C" stroke-width="1"/><path d="M${x-4*s} ${y-8*s} l1.6 -2.6 l1.6 2.6Z M${x+1*s} ${y-8*s} l1.6 -2.6 l1.6 2.6Z M${x-4*s} ${y-4*s} q${4*s} ${2.4*s} ${8*s} 0" stroke="#1A0A02" stroke-width="1" fill="#1A0A02"/>`;
  function wGhost(){/* a kid in a white sheet with eye holes, carrying a pail */
   let s=leg(-4,-26,'#2A2A3A','#2A1A10',0)+leg(4,-26,'#2A2A3A','#2A1A10',1);
   s+=`<path d="M-16 -16 C-16 -52 -14 -80 0 -80 C14 -80 16 -52 16 -16 Q10 -22 6 -14 Q0 -22 -6 -14 Q-10 -22 -16 -16Z" fill="#F4F0F8" stroke="#B8B0C8" stroke-width="1"/>`;
   s+=`<ellipse cx="-7" cy="-62" rx="3" ry="4.2" fill="#1A1420"/><ellipse cx="3" cy="-62" rx="3" ry="4.2" fill="#1A1420"/><path d="M-12 -40 Q-4 -30 2 -34" stroke="#C8C0D8" stroke-width="1.2" fill="none"/>`;
   s+=`<g class="swing">${pail(-16,-20,1)}</g>`;
   return `<g class="wk" data-off="0" data-s=".95">${s}</g>`}
  function wWitch(){/* a kid witch: purple dress, striped tights, big hat, broom over the shoulder */
   let s=`<g class="lg" data-x="-4" data-p="-24" data-ph="0"><path d="M-4 -24 L-4 -3" stroke="#1A1A1A" stroke-width="6" stroke-linecap="round" stroke-dasharray="4 3"/><path d="M-4 -24 L-4 -3" stroke="#F07A1E" stroke-width="6" stroke-dasharray="3 4" stroke-dashoffset="3"/><path d="M-2 -1 h-8 a3 3 0 0 1 0 -5 h6Z" fill="#111"/></g><g class="lg" data-x="4" data-p="-24" data-ph="1"><path d="M4 -24 L4 -3" stroke="#1A1A1A" stroke-width="6" stroke-linecap="round" stroke-dasharray="4 3"/><path d="M4 -24 L4 -3" stroke="#F07A1E" stroke-width="6" stroke-dasharray="3 4" stroke-dashoffset="3"/><path d="M6 -1 h-8 a3 3 0 0 1 0 -5 h6Z" fill="#111"/></g>`;
   s+=`<path d="M38 -86 L-30 -50" stroke="#7A4A22" stroke-width="3" stroke-linecap="round"/><path d="M38 -86 l14 -4 l2 6 l-2 6 l-14 -2Z" fill="#C8963A"/>`;
   s+=`<path d="M-12 -58 h24 l7 34 h-38Z" fill="#6B2E9A"/><path d="M-12 -58 h24" stroke="#4A1E70" stroke-width="3"/>`;
   s+=face(0,-68,10,'#C8401E')+`<path d="M-15 -74 h30 l-3 -3 h-24Z" fill="#141018"/><path d="M-9 -77 L2 -104 L9 -77Z" fill="#141018"/><rect x="-8" y="-80" width="16" height="3" fill="#F07A1E"/>`;
   s+=`<g class="swing">${pail(-16,-28,.9)}</g>`;
   return `<g class="wk" data-off="8" data-s=".95">${s}</g>`}
  function wFamily(){/* a parent holding hands with a small kid in a skeleton suit */
   let s=leg(14,-44,'#2A2440','#15121A',0)+leg(24,-44,'#2A2440','#15121A',1);
   s+=`<path d="M5 -90 h28 l5 48 h-38Z" fill="#4A5A3A"/><rect x="7" y="-92" width="24" height="7" rx="3.5" fill="#C8963A"/>`+face(19,-102,11,'#3A2A1A',skin2);
   s+=`<path d="M10 -82 L-2 -56" stroke="#4A5A3A" stroke-width="6" stroke-linecap="round"/>`;
   /* skeleton kid */
   s+=leg(-22,-22,'#141018','#141018',1)+leg(-15,-22,'#141018','#141018',0);
   s+=`<rect x="-30" y="-50" width="22" height="30" rx="7" fill="#141018"/><path d="M-19 -48 V-26 M-26 -44 h14 M-27 -39 h16 M-26 -34 h14 M-24 -29 h10" stroke="#EDE8F0" stroke-width="1.8" stroke-linecap="round"/>`;
   s+=`<path d="M-10 -46 L-2 -54" stroke="#141018" stroke-width="5" stroke-linecap="round"/><path d="M-10 -46 L-3 -53" stroke="#EDE8F0" stroke-width="1.2"/>`;
   s+=`<circle cx="-19" cy="-60" r="10" fill="#141018"/><circle cx="-21" cy="-61" r="7.5" fill="#EDE8F0"/><circle cx="-24" cy="-62" r="2" fill="#141018"/><circle cx="-18.5" cy="-62" r="2" fill="#141018"/><path d="M-25 -56.5 h7" stroke="#141018" stroke-width="1"/><path d="M-23 -58 v3 M-21 -58 v3 M-19 -58 v3" stroke="#141018" stroke-width=".8"/>`;
   s+=`<g class="swing">${pail(-34,-26,.8)}</g>`;
   return `<g class="wk" data-off="15" data-s=".9">${s}</g>`}
  function wPumpkinKid(){/* a little one dressed as a pumpkin, green leaf hat */
   let s=leg(-5,-18,'#2E5A2A','#1A2A10',0)+leg(5,-18,'#2E5A2A','#1A2A10',1);
   s+=`<ellipse cx="0" cy="-34" rx="20" ry="18" fill="#F07A1E" stroke="#B04A10" stroke-width="1.2"/><path d="M-8 -50 Q-11 -34 -8 -18 M8 -50 Q11 -34 8 -18 M0 -52 V-16" stroke="#C85A14" stroke-width="1.4" fill="none"/>`;
   s+=`<path d="M-12 -38 L-24 -26" stroke="#F07A1E" stroke-width="5" stroke-linecap="round"/>`;
   s+=face(0,-60,9,null)+`<path d="M-9 -64 C-9 -74 9 -74 9 -64Z" fill="#3A7A2A"/><path d="M0 -71 q2 -6 6 -7" stroke="#3A7A2A" stroke-width="2.4" fill="none" stroke-linecap="round"/>`;
   s+=`<g class="swing">${pail(-26,-18,.8)}</g>`;
   return `<g class="wk" data-off="22" data-s=".9">${s}</g>`}
  function walkers(){return wGhost()+wWitch()+wFamily()+wPumpkinKid()}

  /* ---------- the spooky table scene (floor at y=0, x from about -300 to 260) ---------- */
  function candle(x,base,h,w,k,p){const drip=[[-.4,.25],[.1,.4],[.35,.18]];let s=`<rect x="${f1(x-w/2)}" y="${f1(base-h)}" width="${w}" height="${h}" rx="3" fill="url(#${p}wax)"/>`;
   s+=`<path d="M${f1(x-w/2)} ${f1(base-h+3)} ${drip.map(([o,l])=>`L${f1(x+o*w-3)} ${f1(base-h+3)} L${f1(x+o*w-3)} ${f1(base-h+h*l)} a3 3 0 0 0 6 0 L${f1(x+o*w+3)} ${f1(base-h+3)}`).join(' ')} L${f1(x+w/2)} ${f1(base-h+3)} L${f1(x+w/2)} ${f1(base-h)} Q${x} ${f1(base-h-5)} ${f1(x-w/2)} ${f1(base-h)}Z" fill="#FFF6E2"/>`;
   s+=`<rect x="${f1(x-w/2+3)}" y="${f1(base-h+8)}" width="4" height="${f1(h-14)}" rx="2" fill="#fff" opacity=".35"/><path d="M${x} ${f1(base-h-2)} v-7" stroke="#2A1A10" stroke-width="1.6"/>`;
   s+=`<circle class="cglow" data-k="${k}" cx="${x}" cy="${f1(base-h-16)}" r="${f1(w*2.4)}" fill="url(#${p}cg)"/><path class="cfl" data-x="${x}" data-y="${f1(base-h-6)}" data-w="${f1(w*.5)}" data-h="${f1(w*1.15)}" data-k="${k}" d="" fill="url(#${p}fl)"/>`;
   return s}
  function blackCat(x,y,s){/* a black cat sitting, facing us, tail curling round */
   const X=v=>f1(x+v*s),Y=v=>f1(y+v*s);
   let o=`<path class="ctail" data-x="${x}" data-y="${y}" data-s="${s}" d="" stroke="#120C18" stroke-width="${f1(11*s)}" fill="none" stroke-linecap="round"/>`;
   o+=`<path d="M${X(-32)} ${Y(0)}C${X(-44)} ${Y(-44)} ${X(-28)} ${Y(-84)} ${X(0)} ${Y(-86)}C${X(28)} ${Y(-84)} ${X(44)} ${Y(-44)} ${X(32)} ${Y(0)}Z" fill="#120C18" stroke="#3A2C4A" stroke-width="${f1(1.6*s)}"/>`;
   o+=`<path d="M${X(-26)} ${Y(-8)}C${X(-36)} ${Y(-44)} ${X(-24)} ${Y(-76)} ${X(-6)} ${Y(-82)}" stroke="#6A4A8A" stroke-width="${f1(2.4*s)}" fill="none" opacity=".55"/>`;
   o+=`<path d="M${X(-14)} ${Y(-44)}V${Y(-4)} M${X(14)} ${Y(-44)}V${Y(-4)}" stroke="#0A060E" stroke-width="${f1(2*s)}" opacity=".9"/><ellipse cx="${X(-11)}" cy="${Y(-3)}" rx="${f1(9*s)}" ry="${f1(5*s)}" fill="#18101E"/><ellipse cx="${X(11)}" cy="${Y(-3)}" rx="${f1(9*s)}" ry="${f1(5*s)}" fill="#18101E"/>`;
   o+=`<g class="chead" data-x="${x}" data-y="${f1(y-90*s)}"><path d="M${X(-26)} ${Y(-104)}L${X(-24)} ${Y(-140)}L${X(-6)} ${Y(-122)}Z M${X(26)} ${Y(-104)}L${X(24)} ${Y(-140)}L${X(6)} ${Y(-122)}Z" fill="#120C18" stroke="#3A2C4A" stroke-width="${f1(1.4*s)}"/><path d="M${X(-21)} ${Y(-110)}L${X(-21)} ${Y(-131)}L${X(-11)} ${Y(-121)}Z M${X(21)} ${Y(-110)}L${X(21)} ${Y(-131)}L${X(11)} ${Y(-121)}Z" fill="#5A3048"/>`;
   o+=`<ellipse cx="${x}" cy="${Y(-102)}" rx="${f1(29*s)}" ry="${f1(25*s)}" fill="#120C18" stroke="#3A2C4A" stroke-width="${f1(1.4*s)}"/>`;
   o+=`<g class="ceyes" data-y="${Y(-106)}">${[-1,1].map(d=>`<ellipse cx="${X(d*11)}" cy="${Y(-106)}" rx="${f1(7*s)}" ry="${f1(7.5*s)}" fill="#C8F04A"/><ellipse cx="${X(d*11)}" cy="${Y(-106)}" rx="${f1(2*s)}" ry="${f1(6.4*s)}" fill="#0A060E"/><circle cx="${X(d*11-2.5)}" cy="${Y(-109)}" r="${f1(1.6*s)}" fill="#fff"/>`).join('')}</g>`;
   o+=`<path d="M${X(-3)} ${Y(-96)}h${f1(6*s)}l${f1(-3*s)} ${f1(3.4*s)}Z" fill="#D8809A"/><path d="M${X(-1)} ${Y(-92)}q${f1(-4*s)} ${f1(4*s)} ${f1(-8*s)} ${f1(1*s)} M${X(1)} ${Y(-92)}q${f1(4*s)} ${f1(4*s)} ${f1(8*s)} ${f1(1*s)}" stroke="#3A2C4A" stroke-width="${f1(1.4*s)}" fill="none"/>`;
   o+=`<path d="M${X(-8)} ${Y(-94)}L${X(-34)} ${Y(-98)} M${X(-8)} ${Y(-92)}L${X(-34)} ${Y(-88)} M${X(8)} ${Y(-94)}L${X(34)} ${Y(-98)} M${X(8)} ${Y(-92)}L${X(34)} ${Y(-88)}" stroke="#C8C0D8" stroke-width="${f1(.9*s)}" opacity=".7"/></g>`;
   return o}
  function cauldron(x,y,s,p){/* a black iron cauldron full of wrapped sweets for trick-or-treaters */
   const X=v=>f1(x+v*s),Y=v=>f1(y+v*s);
   let o=`<ellipse cx="${x}" cy="${Y(2)}" rx="${f1(70*s)}" ry="${f1(10*s)}" fill="#000" opacity=".45"/>`;
   o+=`<path d="M${X(-42)} ${Y(-6)}l-8 10 M${X(42)} ${Y(-6)}l8 10 M${X(0)} ${Y(-2)}v8" stroke="#14111A" stroke-width="${f1(6*s)}" stroke-linecap="round"/>`;
   o+=`<path d="M${X(-62)} ${Y(-92)}C${X(-74)} ${Y(-40)} ${X(-50)} ${Y(-4)} ${X(0)} ${Y(-4)}C${X(50)} ${Y(-4)} ${X(74)} ${Y(-40)} ${X(62)} ${Y(-92)}Z" fill="url(#${p}iron)" stroke="#05040A" stroke-width="1.4"/>`;
   /* sweets heaped above the rim */
   const r=rnd(9);const CL=[['#9B5BE0','#FFE14A'],['#F07A1E','#FFF0D0'],['#3AA85A','#FFF'],['#E04A6A','#FFE14A'],['#3A7AE0','#FFF']];
   let heap=`<path d="M${X(-58)} ${Y(-92)}Q${X(0)} ${Y(-136)} ${X(58)} ${Y(-92)}Z" fill="#3A1A10"/>`;
   [[-36,-100,-20],[-12,-112,15],[16,-110,-8],[38,-98,22],[0,-124,4],[-24,-118,30],[28,-122,-26],[-46,-94,10],[48,-92,-12]].forEach(([dx,dy,rot],k)=>{const c=CL[k%5];heap+=`<g transform="rotate(${rot} ${X(dx)} ${Y(dy)})">${candy(+X(dx),+Y(dy),.44*s,c[0],c[1])}</g>`});
   heap+=candyCorn(+X(8),+Y(-128),.7*s,20)+candyCorn(+X(-30),+Y(-126),.6*s,-30);
   o+=heap;
   o+=`<ellipse cx="${x}" cy="${Y(-92)}" rx="${f1(64*s)}" ry="${f1(9*s)}" fill="none" stroke="#2A2632" stroke-width="${f1(7*s)}"/><path d="M${X(-60)} ${Y(-90)}Q${X(0)} ${Y(-80)} ${X(60)} ${Y(-90)}" stroke="#4A4458" stroke-width="${f1(2*s)}" fill="none"/>`;
   o+=`<path d="M${X(-44)} ${Y(-70)}C${X(-50)} ${Y(-46)} ${X(-40)} ${Y(-24)} ${X(-24)} ${Y(-14)}" stroke="#fff" stroke-width="${f1(4*s)}" fill="none" opacity=".18" stroke-linecap="round"/>`;
   o+=`<circle cx="${X(-66)}" cy="${Y(-80)}" r="${f1(7*s)}" fill="none" stroke="#2A2632" stroke-width="${f1(3*s)}"/><circle cx="${X(66)}" cy="${Y(-80)}" r="${f1(7*s)}" fill="none" stroke="#2A2632" stroke-width="${f1(3*s)}"/>`;
   return o}
  function sceneDefs(p){return `<linearGradient id="${p}wood" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5A3820"/><stop offset="1" stop-color="#2A180C"/></linearGradient><linearGradient id="${p}wood2" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#2A180C"/><stop offset=".5" stop-color="#4A2C16"/><stop offset="1" stop-color="#1E1008"/></linearGradient>
   <linearGradient id="${p}wax" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#D8CCB0"/><stop offset=".4" stop-color="#FFF6E2"/><stop offset="1" stop-color="#B8A888"/></linearGradient>
   <radialGradient id="${p}fl" cx=".5" cy=".75" r=".7"><stop offset="0" stop-color="#FFFFFF"/><stop offset=".35" stop-color="#FFE48A"/><stop offset=".8" stop-color="#FF9A2A"/><stop offset="1" stop-color="#E0501A"/></radialGradient>
   <radialGradient id="${p}cg"><stop offset="0" stop-color="#FFC060" stop-opacity=".55"/><stop offset="1" stop-color="#FFC060" stop-opacity="0"/></radialGradient>
   <radialGradient id="${p}gl"><stop offset="0" stop-color="#FF9A30" stop-opacity=".38"/><stop offset="1" stop-color="#FF9A30" stop-opacity="0"/></radialGradient>
   <radialGradient id="${p}iron" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#4A4458"/><stop offset=".6" stop-color="#1C1922"/><stop offset="1" stop-color="#08070C"/></radialGradient>`}
  function litPumpkin(cx,cy,w,h,face,k){/* pumpkin() with its inner glow wrapped so it can flicker */
   return `<g class="pk" data-k="${k}">${pumpkin(cx,cy,w,h,face)}</g>`}
  function tableScene(sc=1,stage='carved'){const p='tb'+(RID++);let s=`<defs>${sceneDefs(p)}</defs>`;
   s+=`<ellipse class="roomglow" cx="-40" cy="-300" rx="440" ry="320" fill="url(#${p}gl)"/>`;
   /* old wooden side table */
   s+=`<ellipse cx="-40" cy="-4" rx="200" ry="14" fill="#000" opacity=".45"/>`;
   [-200,100].forEach(x=>{s+=`<path d="M${x} -232 L${x+4} -2 L${x+18} -2 L${x+22} -232Z" fill="url(#${p}wood2)"/>`});
   s+=`<rect x="-196" y="-120" width="310" height="10" fill="#2A180C"/>`;
   s+=`<rect x="-222" y="-246" width="370" height="18" rx="3" fill="url(#${p}wood)"/><rect x="-222" y="-246" width="370" height="4" rx="2" fill="#7A5232"/><rect x="-210" y="-228" width="346" height="16" fill="#2E1A0C"/>`;
   /* a cobweb in the corner under the table top, with a spider letting itself down */
   s+=`<g opacity=".8">${cobweb(-178,-212,62,0,Math.PI*.5,6,4)}</g><path class="sthr" d="" stroke="#fff" stroke-width="1.2" opacity=".8"/><g class="spd" data-x="-130" data-top="-212">${spider(0,0,.7)}</g>`;
   /* on the table: big carved pumpkin, a smaller one, three candles */
   const big=stage==='whole'?'none':'spooky',small=stage==='carved'||stage==='night'?'smile':'none';
   s+=litPumpkin(-150,-276,74,58,small,1);
   s+=candle(70,-246,92,20,0,p)+candle(100,-246,130,22,1,p)+candle(126,-246,66,18,2,p);
   s+=`<rect x="56" y="-252" width="84" height="7" rx="3" fill="#1E1A22"/>`;
   s+=litPumpkin(-50,-306,140,112,big,0);
   /* floor: another jack-o'-lantern, the candy cauldron, the cat */
   s+=litPumpkin(-215,-46,108,86,stage==='whole'?'none':'smile',2);
   s+=cauldron(150,-2,.85,p);
   s+=blackCat(10,0,1.1);
   return `<g class="tscene" data-stage="${stage}" transform="scale(${sc})">${s}</g>`}
  function roomBats(W,H,n){return `<g class="rbats" opacity="0">${Array.from({length:n},(_,k)=>`<g class="rbat" data-k="${k}" data-w="${W}" data-h="${H}">${batSil(0,0,W/60*(.7+(k%3)*.25),'#0A0612')}</g>`).join('')}</g>`}

  /** The two shapes: wide (TVs, computers) and tall (phones). The scene stays left of the profiles. */
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1590, 140, 280, 350], table: [310, 866, 1.1], bats: 9 },
    tall: { w: 390, h: 844, win: null, table: [215, 812, 0.66], bats: 5 },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t) } which poses everything at t seconds.
   * opts: { layout: "wide"|"tall", stage: "whole"|"half"|"carved"|"night" (default: today's), align }
   */
  function build(svg, opts) {
    const L = LAYOUTS[(opts && opts.layout) || "wide"];
    const st = (opts && opts.stage) || stage(new Date());
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", (opts && opts.align) || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win);
    svg.innerHTML = rm.bg + `<g transform="translate(${L.table[0]} ${L.table[1]})">${tableScene(L.table[2], st)}</g>` + rm.vig +
      (st === "night" ? roomBats(L.w, L.h, L.bats).replace('opacity="0"', "") : "");
    return pose(svg, st === "night");
  }

  /* gathers every moving part, and returns at(t) */
  function pose(root, night) {
    const q = (s) => [...root.querySelectorAll(s)];
    const bulbs = q(".bulbh"), glows = q(".roomglow"), cgl = q(".cglow"), pks = q(".pk");
    const fls = q(".cfl").map((e) => ({ e, x: +e.dataset.x, y: +e.dataset.y, w: +e.dataset.w, h: +e.dataset.h, k: +e.dataset.k }));
    const pkLights = pks.map((g) => ({ k: +g.dataset.k, els: [...g.querySelectorAll('g[fill="#FFC23A"]')] }));
    const tails = q(".ctail").map((e) => ({ e, x: +e.dataset.x, y: +e.dataset.y, s: +e.dataset.s }));
    const eyes = q(".ceyes"), heads = q(".chead");
    const spd = q(".tscene").map((g) => ({ s: g.querySelector(".spd"), th: g.querySelector(".sthr") }));
    const wb = q(".wbats").map((g) => ({ ww: +g.closest("svg").getAttribute("width"), wh: +g.closest("svg").getAttribute("height"), b: [...g.querySelectorAll(".wbat")] }));
    const leaves = q(".lf").map((c, k) => ({ c, x: +c.dataset.x, y: +c.dataset.y, d: +c.dataset.d, H: +c.dataset.wh, n: 1 + (k % 2) }));
    const walkers = q(".walkers").map((W) => ({ ww: +W.dataset.w, wh: +W.dataset.h, wk: [...W.querySelectorAll(".wk")] }));
    const rbats = q(".rbat").map((b) => ({ b, k: +b.dataset.k, W: +b.dataset.w, H: +b.dataset.h }));
    // speeds that make whole turns in one loop, so the video joins up
    const s14 = wq(1.4), s06 = wq(0.6), s7 = wq(7), s33 = wq(3.3), s9 = wq(9), s43 = wq(4.3), s83 = wq(8.3), s31 = wq(3.1);
    const s13 = wq(1.3), s05 = wq(0.5), s045 = wq(0.45), s14b = wq(14), s16 = wq(16), s09 = wq(0.9), s7w = wq(7);
    function at(t) {
      bulbs.forEach((b) => { const k = +b.dataset.k; b.setAttribute("opacity", (0.7 + 0.3 * Math.sin(t * s14 + k * 1.9) * Math.sin(t * s06 + k)).toFixed(3)); });
      glows.forEach((g) => g.setAttribute("opacity", ((night ? 1 : 0.85) + 0.12 * Math.sin(t * s7) * Math.sin(t * s33)).toFixed(3)));
      fls.forEach((F) => F.e.setAttribute("d", flameD(F.x, F.y, F.w, F.h, t, F.k * 2.1)));
      cgl.forEach((c) => { const k = +c.dataset.k; c.setAttribute("opacity", (0.75 + 0.25 * Math.sin(t * s9 + k) * Math.sin(t * s43 + k * 2)).toFixed(3)); });
      // candlelight inside each jack-o'-lantern flickers
      pkLights.forEach(({ k, els }) => { const f = 0.78 + 0.22 * Math.sin(t * s83 + k * 1.7) * Math.sin(t * s31 + k); els.forEach((e) => e.setAttribute("opacity", f.toFixed(3))); });
      tails.forEach(({ e, x, y, s }) => { const w = Math.sin(t * s13) * 18; e.setAttribute("d", `M${f1(x + 24 * s)} ${f1(y - 8 * s)}C${f1(x + 70 * s)} ${f1(y - 6 * s)} ${f1(x + 80 * s + w * 0.3 * s)} ${f1(y - 50 * s)} ${f1(x + 62 * s + w * s)} ${f1(y - 86 * s)}`); });
      const blink = (t % 6) < 0.16;
      eyes.forEach((e) => e.setAttribute("transform", blink ? `translate(0 ${e.dataset.y}) scale(1 .1) translate(0 ${-e.dataset.y})` : ""));
      heads.forEach((h) => h.setAttribute("transform", `rotate(${f1(Math.sin(t * s05) * 4)} ${h.dataset.x} ${h.dataset.y})`));
      spd.forEach((S) => { if (!S.s) return; const x = +S.s.dataset.x, top = +S.s.dataset.top; const d = 40 + 70 * (0.5 - 0.5 * Math.cos(t * s045)); S.s.setAttribute("transform", `translate(${x} ${f1(top + d)})`); S.th.setAttribute("d", `M${x} ${top}V${f1(top + d - 12)}`); });
      wb.forEach((W) => W.b.forEach((b, k) => { const P = [10, 15, 7.5][k % 3]; const a = ((t + k * 3.7) % P) / P; const x = -20 + (W.ww + 40) * a, y = W.wh * (0.12 + k * 0.07) + Math.sin(a * 12 + k) * 6; const fl = 0.6 + 0.4 * Math.abs(Math.sin(t * s14b + k)); b.setAttribute("transform", `translate(${f1(x)} ${f1(y)}) scale(1 ${fl.toFixed(2)})`); }));
      leaves.forEach((L) => { const span = L.H + 10; const yy = ((L.y + t * span * L.n / LOOP) % span) - 5; L.c.setAttribute("transform", `translate(${f1(L.x + Math.sin(t * s09 + L.d) * 10)} ${f1(yy)}) rotate(${f1((t * 360 * L.n * 3 / LOOP + L.d * 60) % 360)})`); });
      rbats.forEach(({ b, k, W, H }) => { const P = [5, 6, 7.5, 10][k % 4]; const a = ((t + k * 0.6) % P) / P; const x = -80 + (W + 160) * a, y = H * (0.06 + (k % 5) * 0.05) + Math.sin(a * TAU * 2 + k) * H * 0.03; const fl = 0.5 + 0.5 * Math.abs(Math.sin(t * s16 + k)); b.setAttribute("transform", `translate(${f1(x)} ${f1(y)}) scale(1 ${fl.toFixed(2)})`); });
      walkers.forEach(({ ww, wh, wk }) => {
        const v = (ww + 300) / 9;
        wk.forEach((g) => {
          const off = +g.dataset.off, sc = 1.5 * (+g.dataset.s); const tt = ((t + LOOP - off) % LOOP); const x = ww + 150 - tt * v;
          const y = groundY(x, ww, wh) + 2; const bob = Math.abs(Math.sin(tt * s7w)) * 1.5;
          g.setAttribute("transform", `translate(${f1(x)} ${f1(y - bob)}) scale(${sc})`);
          g.querySelectorAll(".lg").forEach((l) => { const sw = Math.sin(tt * s7w + (+l.dataset.ph) * Math.PI) * 22; l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} ${l.dataset.p})`); });
          const sp = g.querySelector(".swing"); if (sp) sp.setAttribute("transform", `rotate(${f1(Math.sin(tt * s7w) * 6)} -16 -40)`);
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

  G.BenflixHalloween = { LOOP, season, isNight, stage, greeting, countdown, homeLine, build, play, LAYOUTS };
})(typeof window !== "undefined" ? window : globalThis);
