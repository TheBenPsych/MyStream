/* Benflix Star Wars Day: the drawing code for the door corners, straight from the approved design sheet
 * (starwars-3-look.html). Used by tools/starwars/pictures.js. All our own drawings, no film designs. */
let UID=0;const u=p=>p+(UID++)+'_';
const f1=v=>(+v).toFixed(1);
const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
const GDEF=`<defs><filter id="Gts" x="-10%" y="-40%" width="120%" height="180%"><feDropShadow dx="0" dy="2" stdDeviation="2.5" flood-color="#000" flood-opacity=".55"/></filter></defs>`;
function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
function rg(p,c,cx=.35,cy=.3,r=.85){return `<radialGradient id="${p}" cx="${cx}" cy="${cy}" r="${r}"><stop offset="0" stop-color="${c[0]}"/><stop offset=".5" stop-color="${c[1]}"/><stop offset="1" stop-color="${c[2]}"/></radialGradient>`}
function lg(p,c,x2=1,y2=0){return `<linearGradient id="${p}" x1="0" y1="0" x2="${x2}" y2="${y2}">${c.map((s,i)=>`<stop offset="${f1(i/(c.length-1))}" stop-color="${s}"/>`).join('')}</linearGradient>`}
const glint=(x,y,s,op=1)=>`<path d="M${f1(x)} ${f1(y-s)}Q${f1(x+s*.14)} ${f1(y-s*.14)} ${f1(x+s)} ${f1(y)}Q${f1(x+s*.14)} ${f1(y+s*.14)} ${f1(x)} ${f1(y+s)}Q${f1(x-s*.14)} ${f1(y+s*.14)} ${f1(x-s)} ${f1(y)}Q${f1(x-s*.14)} ${f1(y-s*.14)} ${f1(x)} ${f1(y-s)}Z" fill="#fff" opacity="${op}"/>`;
function smooth(P){let d=`M${f1(P[0][0])} ${f1(P[0][1])}`;const n=P.length,g=i=>P[Math.max(0,Math.min(n-1,i))];
 for(let i=0;i<n-1;i++){const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);d+=`C${f1(p1[0]+(p2[0]-p0[0])/6)} ${f1(p1[1]+(p2[1]-p0[1])/6)} ${f1(p2[0]-(p3[0]-p1[0])/6)} ${f1(p2[1]-(p3[1]-p1[1])/6)} ${f1(p2[0])} ${f1(p2[1])}`}return d}
const CRAWL='#FFE81F';
function saber(x1,y1,x2,y2,col,o={}){const p=u('sb');const L=Math.hypot(x2-x1,y2-y1),a=Math.atan2(y2-y1,x2-x1)*180/Math.PI;const hl=o.hilt??36,bw=o.w??5;
 let s=`<defs><filter id="${p}g" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="${f1(bw*1.4)}"/></filter>${lg(p+'h',['#3A404C','#E4E9F0','#9AA4B4','#4E5868'],0,1)}</defs><g transform="translate(${x1} ${y1}) rotate(${f1(a)})">`;
 s+=`<rect x="${hl-2}" y="${f1(-bw*1.6)}" width="${f1(L-hl+2)}" height="${f1(bw*3.2)}" rx="${f1(bw*1.6)}" fill="${col}" opacity=".9" filter="url(#${p}g)"/>`;
 s+=`<rect x="${hl-2}" y="${f1(-bw*.75)}" width="${f1(L-hl+2)}" height="${f1(bw*1.5)}" rx="${f1(bw*.75)}" fill="${col}"/><rect x="${hl-2}" y="${f1(-bw*.4)}" width="${f1(L-hl)}" height="${f1(bw*.8)}" rx="${f1(bw*.4)}" fill="#fff"/>`;
 s+=`<rect x="0" y="${f1(-bw*1.25)}" width="${hl}" height="${f1(bw*2.5)}" rx="2" fill="url(#${p}h)" stroke="#1A1E26" stroke-width="1"/>`;
 for(let k=0;k<5;k++)s+=`<rect x="${f1(4+k*hl*.11)}" y="${f1(-bw*1.25)}" width="${f1(hl*.05)}" height="${f1(bw*2.5)}" fill="#1A1E26"/>`;
 s+=`<rect x="${f1(hl*.66)}" y="${f1(-bw*1.55)}" width="${f1(hl*.14)}" height="${f1(bw*.5)}" rx="1" fill="#C0302A"/><rect x="${f1(hl-5)}" y="${f1(-bw*1.45)}" width="5" height="${f1(bw*2.9)}" rx="1" fill="#C9D2DE" stroke="#1A1E26" stroke-width=".6"/><rect x="-3" y="${f1(-bw*.9)}" width="4" height="${f1(bw*1.8)}" rx="1" fill="#4E5868"/>`;
 return s+`</g>`}
function stars(R,n,X,Y,w,h){let s='';for(let k=0;k<n;k++)s+=`<circle cx="${f1(X+R()*w)}" cy="${f1(Y+R()*h)}" r="${f1(.5+R()*1.3)}" fill="#fff" opacity="${f1(.35+R()*.65)}"/>`;return s}
const VIG=(p,X,w=210,h=190,op=.95)=>`<defs><radialGradient id="${p}v" cx="1" cy="0" r="1"><stop offset="0" stop-color="#03040A" stop-opacity="${op}"/><stop offset=".62" stop-color="#03040A" stop-opacity="${f1(op*.8)}"/><stop offset="1" stop-color="#03040A" stop-opacity="0"/></radialGradient></defs><rect x="${X-w}" y="0" width="${w}" height="${h}" fill="url(#${p}v)"/>`;

/* ===== idea A: iconic moments, a different one in each door's corner ===== */
function crawl(X){const p=u('cr');let s=VIG(p,X,230,200)+stars(rnd(5),26,X-200,0,200,170);
 const L=['MAY THE 4TH','It is a period of','popcorn and movie','nights. New films','are waiting in','the Benflix saga…'];
 /* perspective: lines get smaller and narrower as they go up and back */
 L.forEach((t,i)=>{const k=L.length-1-i;const y=124-k*18+k*k*1.3;const sz=12.5-k*1.5;const cx=X-82;s+=`<text x="${cx}" y="${f1(y)}" font-family="'News Cycle','Franklin Gothic Medium',sans-serif" font-weight="700" font-size="${f1(sz)}" fill="${CRAWL}" text-anchor="middle" textLength="${f1(128-k*19)}" lengthAdjust="spacingAndGlyphs" opacity="${f1(1-k*.12)}">${t}</text>`});
 return s}
function twinSuns(X,o={}){const p=u('ts');const w=o.w??185,h=o.h??170;let s=`<defs>${lg(p+'s',['#2A1840','#9A3E4A','#F08A3A','#FFC870'],0,1)}<clipPath id="${p}c"><path d="M${X-w} 0H${X}V${h}C${X-w*.3} ${h} ${X-w*.75} ${h*.6} ${X-w} 0Z"/></clipPath>${blur(p+'b',6)}</defs>`;
 s+=`<g clip-path="url(#${p}c)"><rect x="${X-w}" y="0" width="${w}" height="${h}" fill="url(#${p}s)"/>${stars(rnd(9),10,X-w,0,w,40)}`;
 s+=`<circle cx="${X-92}" cy="${h*.62}" r="30" fill="#FFF2C8" opacity=".5" filter="url(#${p}b)"/><circle cx="${X-92}" cy="${h*.62}" r="17" fill="#FFF6DA"/><circle cx="${X-48}" cy="${h*.7}" r="11" fill="#FFB060"/>`;
 s+=`<path d="${smooth([[X-w,h*.78],[X-w*.7,h*.72],[X-w*.45,h*.8],[X-w*.2,h*.74],[X,h*.78]])}V${h}H${X-w}Z" fill="#5A2E2A"/>`;
 s+=`<path d="${smooth([[X-w,h*.9],[X-w*.6,h*.84],[X-w*.3,h*.92],[X,h*.86]])}V${h}H${X-w}Z" fill="#3A1C1E"/>`;
 /* a little domed homestead and a tall moisture tower, in silhouette */
 const hx=X-62,hy=h*.83;s+=`<path d="M${hx-18} ${hy}A18 14 0 0 1 ${hx+18} ${hy}Z" fill="#2A1416"/><rect x="${hx-5}" y="${hy-6}" width="10" height="6" fill="#1A0C0E"/>`;
 return s+`</g>`}
function hologram(X){const p=u('hg');let s=VIG(p,X,200,180,.85)+`<defs>${blur(p+'b',4)}<clipPath id="${p}k"><circle cx="${X-74}" cy="64" r="38"/></clipPath></defs>`;const cx=X-74,cy=64;
 s+=`<path d="M${cx-30} 128L${cx-38} 104L${cx+38} 104L${cx+30} 128Z" fill="#7FD8FF" opacity=".16"/>`;
 s+=`<circle cx="${cx}" cy="${cy}" r="44" fill="#6FD0FF" opacity=".25" filter="url(#${p}b)"/><circle cx="${cx}" cy="${cy}" r="38" fill="#4FB8FF" opacity=".28" stroke="#BFEFFF" stroke-width="1.6"/>`;
 s+=`<g clip-path="url(#${p}k)" stroke="#BFEFFF" fill="none" opacity=".8">`;for(let k=-3;k<=3;k++)s+=`<ellipse cx="${cx}" cy="${cy}" rx="${f1(38*Math.cos(k*.4))}" ry="${f1(38)}" stroke-width="1"/>`;for(let k=-3;k<=3;k++)s+=`<path d="M${cx-40} ${cy+k*11}H${cx+40}" stroke-width="1"/>`;for(let y=cy-40;y<cy+40;y+=3)s+=`<path d="M${cx-40} ${y}H${cx+40}" stroke-width=".5" opacity=".35"/>`;s+=`</g>`;
 s+=`<path d="M${cx-36} ${cy+6}H${cx+36}" stroke="#E8FBFF" stroke-width="2" opacity=".6"/>`;
 s+=`<ellipse cx="${cx}" cy="134" rx="40" ry="9" fill="#2A303A" stroke="#8C96A6" stroke-width="1.4"/><ellipse cx="${cx}" cy="131" rx="30" ry="6" fill="#4FB8FF" opacity=".7"/><rect x="${cx-40}" y="134" width="80" height="8" fill="#1A1E26"/><ellipse cx="${cx}" cy="142" rx="40" ry="8" fill="#1A1E26"/>`;
 return s}
function crossedSabers(X){const p=u('cs');let s=VIG(p,X,210,190,.8);s+=saber(X-170,150,X-20,24,'#3E9BFF',{hilt:34,w:4.5})+saber(X-20,150,X-170,24,'#FF2A2A',{hilt:34,w:4.5});
 s+=`<circle cx="${X-95}" cy="87" r="14" fill="#fff" opacity=".55" filter="url(#Gts)"/>`;return s}
function hyperspace(X){const p=u('hy');let s=VIG(p,X,230,200,.95);const r=rnd(31);const ox=X-80,oy=70;
 for(let k=0;k<70;k++){const a=r()*Math.PI*2,l0=6+r()*18,l1=l0+30+r()*80;s+=`<path d="M${f1(ox+Math.cos(a)*l0)} ${f1(oy+Math.sin(a)*l0)}L${f1(ox+Math.cos(a)*l1)} ${f1(oy+Math.sin(a)*l1)}" stroke="${r()<.35?'#BFE0FF':'#fff'}" stroke-width="${f1(.8+r()*1.6)}" stroke-linecap="round" opacity="${f1(.45+r()*.55)}"/>`}
 return s+`<circle cx="${ox}" cy="${oy}" r="16" fill="#DDEFFF" opacity=".55" filter="url(#${p}g)"/><defs>${blur(p+'g',8)}</defs>`}
function cube(X){const p=u('cu');let s=VIG(p,X,200,180,.85)+`<defs>${blur(p+'b',8)}</defs>`;const cx=X-80,cy=70,S=34;
 s+=`<circle cx="${cx}" cy="${cy}" r="56" fill="#4FB8FF" opacity=".3" filter="url(#${p}b)"/>`;
 const top=`M${cx} ${cy-S}L${cx+S} ${cy-S*.5}L${cx} ${cy}L${cx-S} ${cy-S*.5}Z`,lf=`M${cx-S} ${cy-S*.5}L${cx} ${cy}L${cx} ${cy+S}L${cx-S} ${cy+S*.5}Z`,rt=`M${cx+S} ${cy-S*.5}L${cx} ${cy}L${cx} ${cy+S}L${cx+S} ${cy+S*.5}Z`;
 s+=`<path d="${top}" fill="#3A6EA8" stroke="#BFEFFF" stroke-width="1.6"/><path d="${lf}" fill="#1C3F6E" stroke="#BFEFFF" stroke-width="1.6"/><path d="${rt}" fill="#264E84" stroke="#BFEFFF" stroke-width="1.6"/>`;
 s+=`<path d="M${cx-S*.6} ${cy-S*.3}L${cx-S*.25} ${cy+S*.55}M${cx+S*.25} ${cy+S*.1}L${cx+S*.7} ${cy-S*.15}L${cx+S*.7} ${cy+S*.3}M${cx-S*.4} ${cy-S*.62}L${cx+S*.4} ${cy-S*.62}" stroke="#E8FBFF" stroke-width="1.8" opacity=".85"/><circle cx="${cx-S*.5}" cy="${cy+S*.05}" r="3" fill="#E8FBFF"/><circle cx="${cx+S*.5}" cy="${cy+S*.05}" r="3" fill="#E8FBFF"/>`;
 s+=`<path d="M${cx-24} ${cy+S+6}L${cx+24} ${cy+S+6}L${cx+18} ${cy+S+20}H${cx-18}Z" fill="#5A5048" stroke="#2A221C"/><rect x="${cx-26}" y="${cy+S+2}" width="52" height="5" rx="2" fill="#8A7E70"/>`;
 return s+glint(cx+S*.2,cy-S*.8,7,.9)}

const MOMENT={movies:crawl,shows:(X)=>twinSuns(X),live:hologram,games:crossedSabers,music:hyperspace,books:cube};
