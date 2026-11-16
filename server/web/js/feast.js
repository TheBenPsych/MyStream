/* Benflix Thanksgiving: the Who's watching room (approved 2026-11-16, thread "Design a Thanksgiving theme for Benflix").
 *
 * A dim, cosy dining room. The feast table sits on the left: a roast turkey with steam rising, a whole
 * pumpkin pie, mashed potatoes, rolls, cranberry sauce and two candles that flicker. A window on the right
 * shows an autumn dusk with leaves falling, and a little parade walks past: a strutting turkey, a kid in a
 * beanie and scarf, someone carrying a warm pie, and a turkey parade balloon on ropes. 16 to 30 November.
 *
 * Like xmas.js, the same drawing is used live on the web and by tools/feast/record.js, which records it as
 * looping videos for the TV and phone app. Everything is a function of the time t (seconds) and repeats
 * every LOOP seconds, so a video of one loop joins up with no jump.
 * The drawing code comes straight from the approved design sheet (thanksgiving-6-feast.html).
 */
(function (G) {
  "use strict";
  const LOOP = 30;
  const TAU = Math.PI * 2;
  /** a speed that fits a whole number of turns into one loop, close to k radians a second */
  const wq = (k) => Math.max(1, Math.round(k * LOOP / TAU)) * TAU / LOOP;

  // ---------- dates ----------
  /** 16 to 30 November (Christmas takes over on 1 December) */
  const season = (d) => d.getMonth() === 10 && d.getDate() >= 16;
  /** "Happy Thanksgiving" all season */
  const greeting = () => "Happy Thanksgiving";

  // ---------- drawing kit (from the approved design sheet) ----------
  let UID=0;const u=p=>p+(UID++)+'_';
  const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
  const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
  const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
  const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
  const f1=v=>(+v).toFixed(1);
  const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
  const MAPLE=[[50,4],[55,20],[64,14],[62,32],[80,22],[76,34],[94,36],[82,46],[90,52],[72,58],[76,68],[58,64],[52,78]];
  function mapleD(cx,cy,s){const P=[...MAPLE,[48,78],...MAPLE.slice(1).reverse().map(([x,y])=>[100-x,y])];const k=s/100,X=x=>cx+(x-50)*k,Y=y=>cy+(y-50)*k;
   let d=`M${f1(X(P[0][0]))} ${f1(Y(P[0][1]))}`;for(let i=0;i<P.length;i++){const a=P[i],b=P[(i+1)%P.length];const mx=(a[0]+b[0])/2,my=(a[1]+b[1])/2;let nx=b[1]-a[1],ny=-(b[0]-a[0]);const L=Math.hypot(nx,ny)||1;nx/=L;ny/=L;if(nx*(mx-50)+ny*(my-46)<0){nx=-nx;ny=-ny}const bow=Math.min(5,Math.hypot(b[0]-a[0],b[1]-a[1])*.2);d+=`Q${f1(X(mx+nx*bow))} ${f1(Y(my+ny*bow))} ${f1(X(b[0]))} ${f1(Y(b[1]))}`}return d+'Z'}
  function leaf(cx,cy,s,rot,cols,id){/* maple leaf with one shaded half and veins */
   const p=u(id||'lf');const [a,b,c]=cols;const sc=s/100;
   const vein=[[50,76,50,8],[50,62,88,38],[50,62,12,38],[50,70,76,64],[50,70,24,64]].map(([x1,y1,x2,y2])=>`M${f1(cx+(x1-50)*sc)} ${f1(cy+(y1-50)*sc)}L${f1(cx+(x2-50)*sc)} ${f1(cy+(y2-50)*sc)}`).join('');
   return `<g transform="rotate(${rot} ${cx} ${cy})"><defs><radialGradient id="${p}g" cx=".45" cy=".35" r=".75"><stop offset="0" stop-color="${a}"/><stop offset=".6" stop-color="${b}"/><stop offset="1" stop-color="${c}"/></radialGradient><clipPath id="${p}c"><path d="${mapleD(cx,cy,s)}"/></clipPath></defs>
   <path d="${mapleD(cx,cy+3*sc,s)}" fill="#000" opacity=".35" filter="url(#${p}b)"/>${blur(p+'b',3*sc+1)}
   <path d="${mapleD(cx,cy,s)}" fill="url(#${p}g)" stroke="${dk(c,.35)}" stroke-width="${f1(1.6*sc+.4)}" stroke-linejoin="round"/>
   <rect x="${cx}" y="${cy-s}" width="${s}" height="${s*2}" fill="#000" opacity=".16" clip-path="url(#${p}c)"/>
   <path d="${vein}" stroke="${lt(a,.35)}" stroke-width="${f1(1.4*sc+.3)}" stroke-linecap="round" opacity=".75" fill="none"/>
   <path d="M${cx} ${f1(cy+26*sc)} q${f1(-2*sc)} ${f1(14*sc)} ${f1(3*sc)} ${f1(26*sc)}" stroke="${dk(c,.2)}" stroke-width="${f1(3*sc)}" stroke-linecap="round" fill="none"/></g>`}
  function pumpkin(cx,by,w,o={}){const p=u('pk');const h=w*.64,cy=by-h/2,top=by-h;const base=o.col||'#F07A1E';
   const L=[[-.30,.27,.30],[.30,.27,.30],[-.15,.25,.12],[.15,.25,.12],[0,.23,0]];
   let s=`<defs><radialGradient id="${p}g" cx=".42" cy=".38" r=".7"><stop offset="0" stop-color="${lt(base,.38)}"/><stop offset=".55" stop-color="${base}"/><stop offset="1" stop-color="${dk(base,.42)}"/></radialGradient>
   <linearGradient id="${p}st" x1="0" x2="1"><stop offset="0" stop-color="#8A7A3A"/><stop offset=".5" stop-color="#5E5424"/><stop offset="1" stop-color="#352E12"/></linearGradient>${blur(p+'b',w*.05)}${blur(p+'h',w*.035)}</defs>`;
   if(!o.noShadow)s+=`<ellipse cx="${cx}" cy="${f1(by+w*.02)}" rx="${f1(w*.5)}" ry="${f1(w*.07)}" fill="#000" opacity=".45" filter="url(#${p}b)"/>`;
   L.forEach(([dx,rx,dd])=>{s+=`<ellipse cx="${f1(cx+dx*w)}" cy="${f1(cy)}" rx="${f1(rx*w)}" ry="${f1(h/2)}" fill="url(#${p}g)" stroke="${dk(base,.5)}" stroke-opacity=".55" stroke-width="${f1(w*.012+.4)}" ${dd?`style="filter:brightness(${1-dd*.9})"`:''}/>`});
   s+=`<ellipse cx="${f1(cx-w*.06)}" cy="${f1(cy-h*.2)}" rx="${f1(w*.07)}" ry="${f1(h*.16)}" fill="#fff" opacity=".35" filter="url(#${p}h)"/>`;
   s+=`<ellipse cx="${cx}" cy="${f1(top+h*.06)}" rx="${f1(w*.11)}" ry="${f1(h*.06)}" fill="${dk(base,.55)}" opacity=".6"/>`;
   /* stem */
   const sw=w*.1;s+=`<path d="M${f1(cx-sw*.6)} ${f1(top+h*.08)} C${f1(cx-sw*.5)} ${f1(top-h*.12)}, ${f1(cx+sw*.2)} ${f1(top-h*.24)}, ${f1(cx+sw*1.1)} ${f1(top-h*.3)} L${f1(cx+sw*1.35)} ${f1(top-h*.2)} C${f1(cx+sw*.6)} ${f1(top-h*.14)}, ${f1(cx+sw*.5)} ${f1(top)}, ${f1(cx+sw*.6)} ${f1(top+h*.08)}Z" fill="url(#${p}st)" stroke="#2A240E" stroke-width="${f1(w*.008+.3)}"/>`;
   s+=`<ellipse cx="${f1(cx+sw*1.22)}" cy="${f1(top-h*.25)}" rx="${f1(sw*.28)}" ry="${f1(sw*.18)}" fill="#B5A86A" transform="rotate(-30 ${f1(cx+sw*1.22)} ${f1(top-h*.25)})"/>`;
   if(!o.plain){/* curly vine and a leaf */
    s+=`<path d="M${f1(cx-sw*.3)} ${f1(top+h*.02)} c${f1(-w*.12)} ${f1(-h*.05)} ${f1(-w*.2)} ${f1(-h*.22)} ${f1(-w*.14)} ${f1(-h*.3)} c${f1(w*.05)} ${f1(-h*.06)} ${f1(w*.1)} 0 ${f1(w*.06)} ${f1(h*.06)} c${f1(-w*.03)} ${f1(h*.04)} ${f1(-w*.07)} ${f1(h*.01)} ${f1(-w*.05)} ${f1(-h*.04)}" fill="none" stroke="#6C8A2E" stroke-width="${f1(w*.02+.3)}" stroke-linecap="round"/>`;
    s+=leaf(cx-w*.3,top+h*.02,w*.36,-62,['#9DBA4A','#5F8A2A','#355A1A']);}
   return `<g>${s}</g>`}
  /* Acorn hanging on twine from a small brass pin, with an autumn oak leaf */
  function oakD(x0,y0,len,wd){/* oak leaf along +y from (x0,y0) */
   const N=40;let L=[],R=[];for(let i=0;i<=N;i++){const t=i/N;const w=Math.pow(Math.sin(Math.PI*Math.min(1,t*1.05)),.75)*wd*(1+.28*Math.sin(t*Math.PI*9))*(t>.95?.4:1);L.push([x0-w,y0+t*len]);R.push([x0+w,y0+t*len])}
   return 'M'+[...L,...R.reverse()].map(([x,y])=>`${f1(x)} ${f1(y)}`).join('L')+'Z'}
  function acorn(cx,y0,w,o={}){const p=u('ac');const capH=w*.42,nutH=w*1.0,ny=y0+capH*.7;
   let s=`<defs><radialGradient id="${p}n" cx=".38" cy=".3" r=".8"><stop offset="0" stop-color="#F2C27A"/><stop offset=".5" stop-color="#C47A32"/><stop offset="1" stop-color="#6A3512"/></radialGradient>
   <linearGradient id="${p}c" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#9A6A3E"/><stop offset="1" stop-color="#5A3A1E"/></linearGradient>
   <clipPath id="${p}k"><path d="M${f1(cx-w*.56)} ${f1(y0+capH)} C${f1(cx-w*.58)} ${f1(y0-capH*.1)}, ${f1(cx+w*.58)} ${f1(y0-capH*.1)}, ${f1(cx+w*.56)} ${f1(y0+capH)}Z"/></clipPath>${blur(p+'b',w*.06)}${blur(p+'h',w*.04)}</defs>`;
   if(!o.noTwine){const py=o.pinY??(y0-w*.7);s+=`<path d="M${cx} ${f1(py)} L${cx} ${f1(y0-w*.05)}" stroke="#D8C49A" stroke-width="${f1(w*.035+.4)}"/>`;
    if(o.maple){s+=leaf(cx+w*.38,py+w*.26,w*.88,34,['#FFD86E','#E8A12A','#9A5A12'])+leaf(cx-w*.36,py+w*.4,w*1.2,-30,['#FF8A4A','#D8401E','#7A160E'])}
    else s+=`<g transform="rotate(62 ${cx} ${f1(py+w*.1)})"><path d="${oakD(cx,py+w*.08,w*.9,w*.2)}" fill="#C8752A" stroke="#7A3E14" stroke-width="${f1(w*.015+.3)}"/><path d="M${cx} ${f1(py+w*.08)}L${cx} ${f1(py+w*.95)}" stroke="#F0B46A" stroke-width="${f1(w*.015+.3)}" opacity=".7"/></g>`;
    s+=`<path d="M${cx} ${f1(py)} L${cx} ${f1(y0-w*.05)}" stroke="#E8D6AA" stroke-width="${f1(w*.045+.4)}"/><path d="M${f1(cx-w*.12)} ${f1(py+w*.16)} q${f1(w*.12)} ${f1(-w*.1)} ${f1(w*.24)} 0 M${f1(cx-w*.1)} ${f1(py+w*.22)} q${f1(w*.1)} ${f1(w*.08)} ${f1(w*.2)} 0" stroke="#E8D6AA" stroke-width="${f1(w*.035+.3)}" fill="none" stroke-linecap="round"/>`;
    s+=`<circle cx="${cx}" cy="${f1(py)}" r="${f1(w*.09)}" fill="#E8C468" stroke="#7A5A1A" stroke-width="${f1(w*.015+.3)}"/><circle cx="${f1(cx-w*.03)}" cy="${f1(py-w*.03)}" r="${f1(w*.03)}" fill="#FFF3C4"/>`;}
   s+=`<ellipse cx="${cx}" cy="${f1(ny+nutH+w*.05)}" rx="${f1(w*.35)}" ry="${f1(w*.07)}" fill="#000" opacity=".35" filter="url(#${p}b)"/>`;
   s+=`<path d="M${f1(cx-w*.46)} ${f1(ny)} C${f1(cx-w*.48)} ${f1(ny+nutH*.55)}, ${f1(cx-w*.2)} ${f1(ny+nutH*.88)}, ${cx} ${f1(ny+nutH)} C${f1(cx+w*.2)} ${f1(ny+nutH*.88)}, ${f1(cx+w*.48)} ${f1(ny+nutH*.55)}, ${f1(cx+w*.46)} ${f1(ny)}Z" fill="url(#${p}n)" stroke="#5A2C0E" stroke-width="${f1(w*.015+.3)}"/>`;
   s+=`<ellipse cx="${f1(cx-w*.18)}" cy="${f1(ny+nutH*.38)}" rx="${f1(w*.08)}" ry="${f1(nutH*.2)}" fill="#fff" opacity=".4" filter="url(#${p}h)"/><circle cx="${cx}" cy="${f1(ny+nutH-w*.02)}" r="${f1(w*.035)}" fill="#4A240C"/>`;
   /* cap with scales */
   let sc='';for(let r=0;r<4;r++){const yy=y0+capH*(.15+r*.24);const n=6+r;for(let k=0;k<n;k++){const xx=cx-w*.56+(k+.5+(r%2)*.5)*(w*1.12/n);sc+=`<path d="M${f1(xx-w*.07)} ${f1(yy)} q${f1(w*.07)} ${f1(capH*.28)} ${f1(w*.14)} 0" fill="none" stroke="#3A2410" stroke-width="${f1(w*.018+.3)}" opacity=".65"/>`}}
   s+=`<path d="M${f1(cx-w*.56)} ${f1(y0+capH)} C${f1(cx-w*.58)} ${f1(y0-capH*.1)}, ${f1(cx+w*.58)} ${f1(y0-capH*.1)}, ${f1(cx+w*.56)} ${f1(y0+capH)} Q${cx} ${f1(y0+capH*1.18)} ${f1(cx-w*.56)} ${f1(y0+capH)}Z" fill="url(#${p}c)" stroke="#2E1C0C" stroke-width="${f1(w*.015+.3)}"/><g clip-path="url(#${p}k)">${sc}</g>`;
   s+=`<rect x="${f1(cx-w*.04)}" y="${f1(y0-w*.1)}" width="${f1(w*.08)}" height="${f1(w*.14)}" rx="${f1(w*.03)}" fill="#4A3018"/>`;
   return `<g>${s}</g>`}
  /* Turkey feather fan tucked into the top-right corner (pivot = corner) */
  function fan(px,py,R,a0,a1,n,o={}){const p=u('fn');let fe='';const fs=[];for(let i=0;i<n;i++){const a=a0+(a1-a0)*i/(n-1);fs.push(a)}
   const feather=a=>`<path transform="translate(${px} ${py}) rotate(${a})" d="M0 0 C${f1(R*.17)} -${f1(R*.08)}, ${f1(R*.5)} -${f1(R*.135)}, ${f1(R*.79)} -${f1(R*.12)} C${f1(R*.93)} -${f1(R*.1)}, ${f1(R)} -${f1(R*.035)}, ${f1(R)} 0 C${f1(R)} ${f1(R*.035)}, ${f1(R*.93)} ${f1(R*.1)}, ${f1(R*.79)} ${f1(R*.12)} C${f1(R*.5)} ${f1(R*.135)}, ${f1(R*.17)} ${f1(R*.08)}, 0 0Z"/>`;
   const bands=[[1.02,'#F3E4C2'],[.9,'#24150C'],[.82,'#B5531E'],[.7,'#7B4A22'],[.52,'#E6CF9E'],[.44,'#5C3518'],[.26,'#3A2210']];
   let s=`<defs><clipPath id="${p}c">${fs.map(feather).join('')}</clipPath><radialGradient id="${p}sh" cx="${px}" cy="${py}" r="${R}" gradientUnits="userSpaceOnUse"><stop offset=".55" stop-color="#fff" stop-opacity="0"/><stop offset=".75" stop-color="#fff" stop-opacity=".18"/><stop offset=".86" stop-color="#fff" stop-opacity="0"/></radialGradient>${blur(p+'b',R*.04)}</defs>`;
   s+=`<g opacity=".5" filter="url(#${p}b)" transform="translate(${f1(-R*.03)} ${f1(R*.04)})">${fs.map(feather).join('').replace(/<path/g,'<path fill="#000"')}</g>`;
   s+=`<g clip-path="url(#${p}c)">${bands.map(([r,c])=>`<circle cx="${px}" cy="${py}" r="${f1(R*r)}" fill="${c}"/>`).join('')}<circle cx="${px}" cy="${py}" r="${R}" fill="url(#${p}sh)"/></g>`;
   s+=fs.map(a=>`<g transform="translate(${px} ${py}) rotate(${a})"><path d="M${f1(R*.08)} 0 L${f1(R*.95)} 0" stroke="#F6E7C6" stroke-width="${f1(R*.012+.3)}" opacity=".55"/></g>`).join('');
   s+=`<g fill="none" stroke="#1A0E06" stroke-opacity=".45" stroke-width="${f1(R*.01+.3)}">${fs.map(feather).join('')}</g>`;
   return `<g>${s}</g>`}

  /* ---------- the object, hung from a pin at (cx,py) ---------- */
  const tack=(x,y,r)=>`<circle cx="${f1(x)}" cy="${f1(y)}" r="${f1(r)}" fill="#E8C468" stroke="#6A4A12" stroke-width="${f1(r*.22+.3)}"/><circle cx="${f1(x-r*.3)}" cy="${f1(y-r*.3)}" r="${f1(r*.33)}" fill="#FFF6D0"/>`;
  function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
  /* smooth closed/open curve through points (Catmull-Rom to Bezier) */
  function smooth(P,closed){const n=P.length;let d=`M${f1(P[0][0])} ${f1(P[0][1])}`;const g=i=>closed?P[(i+n)%n]:P[Math.max(0,Math.min(n-1,i))];
   for(let i=0;i<(closed?n:n-1);i++){const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);d+=`C${f1(p1[0]+(p2[0]-p0[0])/6)} ${f1(p1[1]+(p2[1]-p0[1])/6)} ${f1(p2[0]-(p3[0]-p1[0])/6)} ${f1(p2[1]-(p3[1]-p1[1])/6)} ${f1(p2[0])} ${f1(p2[1])}`}return d+(closed?'Z':'')}
  /* one flame tongue: base centre bx,by, width w, height h, wobble phase */
  function flameD(bx,by,w,h,t,ph,sp=1){const P=[];const N=7;
   for(let i=0;i<=N;i++){const s=i/N;const wob=Math.sin(wq(5.2*sp)*t+ph+s*3.1)*w*.16*s+Math.sin(wq(8.7*sp)*t+ph*1.7+s*5)*w*.07*s;const hw=w/2*Math.pow(1-s,.85)*(1+.25*Math.sin(s*Math.PI));P.push([bx-hw+wob,by-s*h*(1+.08*Math.sin(wq(6*sp)*t+ph))])}
   const tipx=bx+Math.sin(wq(4.1*sp)*t+ph)*w*.28,tipy=by-h*(1.04+.1*Math.sin(wq(6.3*sp)*t+ph*2));
   const R=[];for(let i=N;i>=0;i--){const s=i/N;const wob=Math.sin(wq(5.2*sp)*t+ph+s*3.1)*w*.16*s+Math.sin(wq(7.9*sp)*t+ph*1.3+s*4.4)*w*.07*s;const hw=w/2*Math.pow(1-s,.85)*(1+.25*Math.sin(s*Math.PI));R.push([bx+hw+wob,by-s*h*(1+.08*Math.sin(wq(6*sp)*t+ph))])}
   const L=P.slice(0,N);return smooth([...L,[tipx,tipy],...R.slice(1)],true)}
  /* ---------- the room ---------- */
  let RID=0;
  function groundY(x,ww,wh){return wh-44+Math.sin(x*.025)*4+Math.sin(x*.061+1)*2}
  function room(W2,H2,win){const id='rm'+(RID++);const fl=H2*.8;
   let s=`<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0E0A08"/><stop offset="1" stop-color="#1C130D"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2A1A10"/><stop offset="1" stop-color="#0E0806"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".62"/></radialGradient></defs>`;
   s+=`<rect width="${W2}" height="${H2}" fill="url(#${id}w)"/>`;for(let x=0;x<W2;x+=W2/24)s+=`<rect x="${f1(x)}" y="0" width="${f1(W2/48)}" height="${fl}" fill="#fff" opacity=".016"/>`;
   /* wainscot panel line */
   s+=`<rect y="${f1(fl-H2*.16)}" width="${W2}" height="${f1(H2*.16)}" fill="#000" opacity=".18"/><rect y="${f1(fl-H2*.16)}" width="${W2}" height="4" fill="#3A2618" opacity=".7"/>`;
   if(win)s+=windowSVG(win);
   s+=`<rect y="${fl}" width="${W2}" height="${H2-fl}" fill="url(#${id}f)"/>`;for(let k=1;k<6;k++)s+=`<path d="M0 ${f1(fl+(H2-fl)*k/6)} H${W2}" stroke="#000" stroke-opacity=".25" stroke-width="2"/>`;
   s+=`<rect y="${fl-10}" width="${W2}" height="10" fill="#160E09"/>`;
   return {bg:s,vig:`<rect width="${W2}" height="${H2}" fill="url(#${id}v)"/>`}}
  function windowSVG([wx,wy,ww,wh]){const id='wn'+(RID++);const r=rnd(91);
   let sky=`<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1B2342"/><stop offset=".55" stop-color="#6A3E4E"/><stop offset=".85" stop-color="#E08A4A"/></linearGradient></defs><rect width="${ww}" height="${wh}" fill="url(#${id}s)"/>`;
   /* distant autumn trees */
   let trees='';for(let k=0;k<9;k++){const x=k*ww/8+r()*14-7,h=60+r()*40,c=['#7A2E14','#9A4A16','#6A3A12','#8A5A1A','#5A2A12'][k%5];trees+=`<rect x="${f1(x-2)}" y="${f1(wh-44-h*.4)}" width="4" height="${f1(h*.4)}" fill="#2A1A10"/><ellipse cx="${f1(x)}" cy="${f1(wh-44-h*.55)}" rx="${f1(18+r()*8)}" ry="${f1(h*.35)}" fill="${c}"/>`}
   let g=`M0 ${f1(groundY(0,ww,wh))}`;for(let x=0;x<=ww;x+=10)g+=`L${x} ${f1(groundY(x,ww,wh))}`;g+=`V${wh} H0Z`;
   let lv='';const LC=['#E8742A','#C8401E','#E8B040','#9A3A14'];for(let k=0;k<30;k++){const x=r()*ww,y=groundY(x,ww,wh)+4+r()*30;lv+=`<ellipse cx="${f1(x)}" cy="${f1(y)}" rx="${f1(2.5+r()*2)}" ry="1.6" fill="${LC[k%4]}" transform="rotate(${f1(r()*60-30)} ${f1(x)} ${f1(y)})"/>`}
   let fall='';for(let k=0;k<12;k++){fall+=`<g class="fleaf" data-x="${f1(r()*ww)}" data-y="${f1(r()*wh)}" data-v="${f1(10+r()*12)}" data-d="${f1(r()*6)}" data-wh="${wh}"><path d="M0 -5 C4 -3 4 3 0 5 C-4 3 -4 -3 0 -5Z" fill="${LC[k%4]}"/></g>`}
   return `<rect x="${wx-14}" y="${wy-14}" width="${ww+28}" height="${wh+28}" rx="6" fill="#2E1E14"/><svg x="${wx}" y="${wy}" width="${ww}" height="${wh}" overflow="hidden">${sky}<circle cx="${ww*.72}" cy="${wh*.22}" r="16" fill="#FFE6B0" opacity=".9"/>${trees}<path d="${g}" fill="#3A2A16"/>${lv}<g class="walkers" data-w="${ww}" data-h="${wh}">${walkers()}</g>${fall}<rect width="${ww}" height="${wh}" fill="#1B2342" opacity=".08"/></svg><rect x="${wx+ww/2-4}" y="${wy}" width="8" height="${wh}" fill="#2E1E14"/><rect x="${wx}" y="${wy+wh/2-4}" width="${ww}" height="8" fill="#2E1E14"/><rect x="${wx-22}" y="${wy+wh+12}" width="${ww+44}" height="10" rx="3" fill="#2E1E14"/>`+
    /* a little sill decoration: two mini pumpkins */ `<g>${pumpkin(wx+22,wy+wh+14,34,{plain:true,noShadow:true})}${pumpkin(wx+ww-26,wy+wh+14,28,{plain:true,noShadow:true,col:'#E9DCC0'})}</g>`}

  /* ---------- people and animals walking past the window (feet at y=0, facing left) ---------- */
  const skin='#E8B48A',skin2='#B97E58';
  function leg(x,hipY,len,col,shoe,ph){return `<g class="lg" data-x="${x}" data-p="${hipY}" data-ph="${ph}"><path d="M${x} ${hipY} L${x} ${-3}" stroke="${col}" stroke-width="6" stroke-linecap="round"/><path d="M${x+2} -1 h-8 a3 3 0 0 1 0 -5 h6Z" fill="${shoe}" stroke="${shoe}" stroke-width="2" stroke-linejoin="round"/></g>`}
  function face(x,y,r,hair,o={}){return `<circle cx="${x}" cy="${y}" r="${r}" fill="${skin}"/><circle cx="${x-r*.45}" cy="${y-r*.1}" r="${r*.13}" fill="#2A1A10"/><path d="M${x-r*.6} ${y+r*.35} q${r*.25} ${r*.2} ${r*.5} 0" stroke="#7A3A20" stroke-width="1.3" fill="none" stroke-linecap="round"/><circle cx="${x-r*.15}" cy="${y+r*.25}" r="${r*.2}" fill="#E07A6A" opacity=".45"/>`+(hair?`<path d="M${x-r} ${y-r*.1} C${x-r} ${y-r*1.3} ${x+r*1.1} ${y-r*1.4} ${x+r} ${y+r*.2} C${x+r*.6} ${y-r*.3} ${x} ${y-r*.6} ${x-r} ${y-r*.1}Z" fill="${hair}"/>`:'')}
  function wTurkey(){/* a turkey strutting, tail fanned */
   let s=`<g class="tail">${fan(10,-36,34,-178,-2,9)}</g>`;
   const tleg=(x,ph)=>`<g class="lg" data-x="${x}" data-p="-16" data-ph="${ph}"><path d="M${x} -16 L${x} -2" stroke="#D8862A" stroke-width="3.2" stroke-linecap="round"/><path d="M${x} -1 l-7 1 M${x} -1 l-3 3 M${x} -1 l4 1" stroke="#D8862A" stroke-width="2.2" stroke-linecap="round"/></g>`;s+=tleg(-4,0)+tleg(6,1);
   s+=`<ellipse cx="2" cy="-30" rx="22" ry="18" fill="#5A3418"/><ellipse cx="-2" cy="-33" rx="18" ry="13" fill="#7A4A22"/><path d="M6 -40 q12 6 10 18 q-8 -4 -16 -2Z" fill="#3E2410"/><path d="M8 -34 l8 6 M6 -30 l9 6" stroke="#C8A070" stroke-width="1.4"/>`;
   s+=`<g class="hd"><path d="M-12 -40 C-20 -48 -22 -58 -18 -64" stroke="#8A5A3A" stroke-width="7" fill="none" stroke-linecap="round"/><circle cx="-19" cy="-66" r="6" fill="#C9D2E0"/><circle cx="-21" cy="-67" r="1.4" fill="#111"/><path d="M-24 -66 l-6 2 l6 2Z" fill="#E8B040"/><path d="M-24 -69 q-5 4 -3 11" stroke="#D8302A" stroke-width="3" fill="none" stroke-linecap="round"/><path d="M-20 -61 q-2 6 2 8 q3 -4 0 -8Z" fill="#D8302A"/></g>`;
   return `<g class="wk" data-off="0" data-s="1" data-turkey="1">${s}</g>`}
  function wKid(){/* kid in a beanie and long scarf */
   let s=leg(-4,-30,30,'#2E3A5A','#4A2A18',0)+leg(4,-30,30,'#2E3A5A','#4A2A18',1);
   s+=`<g class="scarf"><path d="M6 -56 C16 -54 22 -50 28 -52 l1 6 C22 -44 14 -48 6 -50Z" fill="#3E7A4A"/></g>`;
   s+=`<rect x="-11" y="-62" width="22" height="34" rx="9" fill="#D8602A"/><path d="M-11 -40 h22" stroke="#B04A1E" stroke-width="2"/>`;
   s+=`<g class="arm" data-px="0" data-py="-56"><path d="M0 -56 L-2 -38" stroke="#C8521E" stroke-width="7" stroke-linecap="round"/><circle cx="-2" cy="-36" r="3.4" fill="#8A3A1A"/></g>`;
   s+=`<rect x="-10" y="-60" width="20" height="6" rx="3" fill="#3E7A4A"/>`+face(0,-72,10,'#5A3418')+`<path d="M-11 -74 C-11 -88 11 -88 11 -74Z" fill="#E8B040"/><rect x="-12" y="-76" width="24" height="5" rx="2.5" fill="#C89020"/><circle cx="0" cy="-86" r="4" fill="#F5E6C0"/>`;
   return `<g class="wk" data-off="7" data-s=".95">${s}</g>`}
  function wPie(){/* grown-up carrying a warm pie */
   let s=leg(-5,-44,44,'#4A3020','#2A1A10',0)+leg(5,-44,44,'#4A3020','#2A1A10',1);
   s+=`<path d="M-14 -88 h28 l4 46 h-36Z" fill="#8E2238"/><path d="M0 -88 v44" stroke="#6E1A2C" stroke-width="2"/><rect x="-12" y="-90" width="24" height="7" rx="3.5" fill="#E8B040"/>`;
   s+=`<path d="M8 -84 C2 -76 -10 -72 -20 -70" stroke="#7E1C30" stroke-width="7" fill="none" stroke-linecap="round"/><path d="M-4 -82 C-8 -76 -14 -72 -20 -70" stroke="#8E2238" stroke-width="7" fill="none" stroke-linecap="round"/>`;
   s+=`<ellipse cx="-24" cy="-70" rx="16" ry="5" fill="#E8E0D0"/><ellipse cx="-24" cy="-72" rx="13" ry="4" fill="#D88A3A"/><path d="M-34 -72 l20 0 M-30 -74 l12 4 M-30 -70 l12 -4" stroke="#F0C080" stroke-width="1.3"/><circle cx="-24" cy="-69" r="0"/>`;
   s+=`<g class="steam">${[0,1,2].map(k=>`<path class="st" data-k="${k}" d="" stroke="#fff" stroke-width="1.6" fill="none" stroke-linecap="round" opacity=".55"/>`).join('')}</g>`;
   s+=face(0,-100,11,'#2A1A10')+`<path d="M-11 -106 C-12 -118 12 -120 12 -104 L12 -96 C8 -104 -2 -108 -11 -106Z" fill="#2A1A10"/>`;
   return `<g class="wk" data-off="13" data-s=".9">${s}</g>`}
  function wBalloon(){/* parade balloon: a big round turkey held on ropes by two handlers */
   const handler=(x,ph)=>`<g transform="translate(${x} 0)">${leg(-3,-26,26,'#2A3A6A','#111',ph)}${leg(3,-26,26,'#2A3A6A','#111',1-ph)}<rect x="-8" y="-50" width="16" height="26" rx="6" fill="#E8B040"/>${face(0,-58,8,null)}<path d="M-9 -60 C-9 -70 9 -70 9 -60Z" fill="#2A3A6A"/><rect x="-11" y="-61" width="14" height="3" rx="1.5" fill="#2A3A6A"/><g class="arm up" data-px="0" data-py="-46"><path d="M0 -46 L2 -64" stroke="#E8B040" stroke-width="5" stroke-linecap="round"/></g></g>`;
   let b=`<g class="bal">${fan(0,-150,78,-186,6,11)}<circle cx="0" cy="-150" r="44" fill="#8A5428"/><circle cx="-6" cy="-156" r="34" fill="#A8683A"/><ellipse cx="-14" cy="-168" rx="12" ry="8" fill="#fff" opacity=".22"/>`+
    `<path d="M-30 -172 C-44 -188 -44 -206 -34 -214" stroke="#B07A4E" stroke-width="12" fill="none" stroke-linecap="round"/><circle cx="-34" cy="-218" r="12" fill="#D8DEEA"/><circle cx="-38" cy="-220" r="2.6" fill="#111"/><path d="M-45 -218 l-9 3 l9 3Z" fill="#E8B040"/><path d="M-43 -224 q-8 6 -5 18" stroke="#D8302A" stroke-width="5" fill="none" stroke-linecap="round"/><path d="M-38 -208 q-3 10 3 13 q5 -7 0 -13Z" fill="#D8302A"/>`+
    `<path d="M-10 -114 l-4 14 M10 -114 l4 14" stroke="#E0902A" stroke-width="5" stroke-linecap="round"/></g>`;
   const ropes=`<path class="rope" data-i="0" d="" stroke="#E8E0C8" stroke-width="1.6" fill="none"/><path class="rope" data-i="1" d="" stroke="#E8E0C8" stroke-width="1.6" fill="none"/>`;
   return `<g class="wk" data-off="20" data-s=".54" data-balloon="1">${ropes}${b}${handler(-40,0)}${handler(40,1)}</g>`}
  function walkers(){return wTurkey()+wKid()+wPie()+wBalloon()}
  function candle(x,by,h,w,cls){return `<g><rect x="${x-w/2}" y="${by-h}" width="${w}" height="${h}" rx="3" fill="#F2E6CC"/><rect x="${x-w/2}" y="${by-h}" width="${w*.35}" height="${h}" rx="3" fill="#fff" opacity=".35"/><path d="M${x} ${by-h} v-6" stroke="#2A1A10" stroke-width="1.6"/><circle class="halo ${cls}" cx="${x}" cy="${by-h-14}" r="${w*1.6}" fill="#FFB050" opacity=".25"/><path class="cflame" data-x="${x}" data-y="${by-h-4}" data-w="${w*.55}" data-h="${w*1.3}" d="" fill="#FFC560"/><path class="cflame2" data-x="${x}" data-y="${by-h-4}" data-w="${w*.28}" data-h="${w*.7}" d="" fill="#FFF3C8"/></g>`}
  let FID=0;const fu=p=>p+(FID++)+'_';
  function rosemary(x,y,len,rot){let s=`<path d="M0 0 L${len} 0" stroke="#4A5A2A" stroke-width="2"/>`;for(let i=2;i<len;i+=4){s+=`<path d="M${i} 0 l3 -6 M${i+2} 0 l3 6" stroke="#3E6A2E" stroke-width="2.2" stroke-linecap="round"/>`}return `<g transform="translate(${x} ${y}) rotate(${rot})">${s}</g>`}
  function orangeSlice(x,y,r){let s=`<ellipse cx="${x}" cy="${y}" rx="${r}" ry="${r*.45}" fill="#F08A1E"/><ellipse cx="${x}" cy="${y-1}" rx="${r*.84}" ry="${r*.37}" fill="#FFC060"/>`;for(let k=0;k<8;k++){const a=k*Math.PI/4;s+=`<path d="M${x} ${y-1} L${f1(x+Math.cos(a)*r*.8)} ${f1(y-1+Math.sin(a)*r*.34)}" stroke="#FFE2A8" stroke-width="1.2"/>`}return s}
  function berries(x,y,n,seed){const r=rnd(seed);let s='';for(let k=0;k<n;k++){const bx=x+(r()-.5)*18,by=y+(r()-.5)*8;s+=`<circle cx="${f1(bx)}" cy="${f1(by)}" r="4.2" fill="#A8102A"/><circle cx="${f1(bx-1.3)}" cy="${f1(by-1.4)}" r="1.3" fill="#fff" opacity=".55"/>`}return s}
  function roastTurkey(x,y,s){const p=fu('rt');const r=rnd(17);
   /* side view, the classic roast: big golden body, one drumstick pointing up with a paper frill */
   const body='M-122 -8 C-134 -62 -84 -122 -10 -124 C62 -126 114 -88 122 -38 C126 -16 116 -6 100 -4 C40 6 -62 6 -122 -8Z';
   let spots='';for(let k=0;k<80;k++){const sx=(r()-.5)*230,sy=-8-r()*112;spots+=`<ellipse cx="${f1(sx)}" cy="${f1(sy)}" rx="${f1(2+r()*6)}" ry="${f1(1.5+r()*3)}" fill="${r()<.55?'#7A3410':'#FFE0A8'}" opacity="${(.1+r()*.16).toFixed(2)}"/>`}
   const drum=(dx,dy,dark)=>`<g transform="translate(${dx} ${dy}) rotate(40)"><path d="M-24 0 C-26 -30 -13 -52 -6 -66 L6 -66 C13 -52 26 -30 24 0 C15 16 -15 16 -24 0Z" fill="url(#${p}dr)" ${dark?'style="filter:brightness(.6)"':''}/>${dark?'':`<ellipse cx="-8" cy="-22" rx="5" ry="17" fill="#FFE6B0" opacity=".4" filter="url(#${p}hb)"/>`}
    <rect x="-5" y="-84" width="10" height="20" rx="4" fill="#F2E6CC"/><circle cx="-5" cy="-86" r="6" fill="#F6ECD6"/><circle cx="5" cy="-86" r="6" fill="#F6ECD6"/>
    <path d="M-11 -90 l3 -13 l3 9 l3 -11 l3 10 l3 -11 l3 9 l3 -13 v17 h-21Z" fill="#fff" stroke="#D8D0C0" stroke-width="1"/><rect x="-11" y="-82" width="22" height="7" rx="2" fill="#fff" stroke="#D8D0C0" stroke-width="1"/></g>`;
   return `<g transform="translate(${x} ${y}) scale(${s})"><defs>
   <radialGradient id="${p}sk" cx=".42" cy=".2" r=".95"><stop offset="0" stop-color="#FCDDA2"/><stop offset=".2" stop-color="#EDA654"/><stop offset=".48" stop-color="#CC742A"/><stop offset=".8" stop-color="#8E4316"/><stop offset="1" stop-color="#5A2A0C"/></radialGradient>
   <radialGradient id="${p}th" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#F0B060"/><stop offset=".55" stop-color="#C46A26"/><stop offset="1" stop-color="#6A2E0C"/></radialGradient>
   <linearGradient id="${p}dr" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#7A3812"/><stop offset=".45" stop-color="#DA8C40"/><stop offset="1" stop-color="#7A3812"/></linearGradient>
   <radialGradient id="${p}pl" cx=".5" cy=".4" r=".7"><stop offset="0" stop-color="#FFFFFF"/><stop offset=".8" stop-color="#E6DFD2"/><stop offset="1" stop-color="#BDB3A2"/></radialGradient>
   <clipPath id="${p}c"><path d="${body}"/></clipPath>${blur(p+'hb',3)}${blur(p+'sh',6)}</defs>
   <ellipse cx="0" cy="10" rx="160" ry="30" fill="#000" opacity=".35" filter="url(#${p}sh)"/>
   <ellipse cx="0" cy="4" rx="158" ry="32" fill="url(#${p}pl)"/><ellipse cx="0" cy="4" rx="158" ry="32" fill="none" stroke="#C8A050" stroke-width="2.5"/><ellipse cx="0" cy="2" rx="132" ry="22" fill="#E8E1D4"/>
   ${rosemary(-152,0,46,-8)}${rosemary(110,2,44,6)}
   ${[[-138,-2],[-110,10],[120,8],[142,-2]].map(([a,b],k)=>`<ellipse cx="${a}" cy="${b}" rx="12" ry="7" fill="${['#3E6A2E','#5A8A3A'][k%2]}" transform="rotate(${k*30-40} ${a} ${b})"/>`).join('')}
   <ellipse cx="0" cy="-2" rx="112" ry="10" fill="#000" opacity=".35" filter="url(#${p}hb)"/>
   ${drum(58,-52,true)}
   <path d="${body}" fill="url(#${p}sk)"/>
   <g clip-path="url(#${p}c)">${spots}
    <path d="M-96 -70 C-60 -104 -10 -112 40 -104" stroke="#fff" stroke-width="10" opacity=".3" fill="none" stroke-linecap="round" filter="url(#${p}hb)"/>
    <ellipse cx="-40" cy="-98" rx="26" ry="9" fill="#fff" opacity=".45" filter="url(#${p}hb)" transform="rotate(-14 -40 -98)"/>
    <path d="M-110 -34 C-80 -50 -60 -40 -50 -14" stroke="#7A3410" stroke-width="5" opacity=".35" fill="none"/>
    <ellipse cx="0" cy="-4" rx="124" ry="18" fill="#5A2A0C" opacity=".4" filter="url(#${p}hb)"/></g>
   <ellipse cx="34" cy="-40" rx="50" ry="38" fill="url(#${p}th)" transform="rotate(-18 34 -40)"/><path d="M-6 -30 C6 -64 52 -78 78 -58" stroke="#FFE6B0" stroke-width="4" opacity=".35" fill="none" filter="url(#${p}hb)"/>
   ${drum(72,-58,false)}
   ${orangeSlice(-128,8,15)}${orangeSlice(128,8,14)}${berries(-92,14,6,3)}${berries(96,14,6,8)}${berries(-10,18,5,4)}
   <g class="steam2">${[0,1,2].map(k=>`<path class="st2" data-k="${k}" d="" stroke="#F4ECE0" stroke-width="9" fill="none" stroke-linecap="round" opacity=".2" filter="url(#${p}hb)"/>`).join('')}</g></g>`}
  function pumpkinPie(x,y,s){const p=fu('pp');const rx=66,ry=20,cy=-18;
   /* crust rim: back scallops behind the filling, front scallops in front of it */
   let back='',front='';for(let k=0;k<28;k++){const a=k/28*Math.PI*2,sn=Math.sin(a);const c=`<ellipse cx="${f1(Math.cos(a)*rx)}" cy="${f1(cy+sn*ry)}" rx="8" ry="${f1(6+sn*1.5)}" fill="${sn>0?'#DE9C50':'#EAB46A'}" stroke="#A8662A" stroke-width="1"/>`;if(sn<0)back+=c;else front+=c}
   const cream=(cx,cy2,sc)=>`<g transform="translate(${cx} ${cy2}) scale(${sc})"><ellipse cx="0" cy="1" rx="13" ry="5" fill="#000" opacity=".18"/><ellipse cx="0" cy="-1" rx="12" ry="5.5" fill="#E6E6EE"/><ellipse cx="0" cy="-4" rx="10" ry="5" fill="#FFFFFF"/><ellipse cx="0" cy="-7.5" rx="7" ry="4" fill="#F6F6FA"/><path d="M-3.5 -9 Q0 -17 3.5 -10Z" fill="#fff"/><path d="M-9 -2 q9 3.5 18 0 M-6 -6 q6 2.5 12 0" stroke="#CDCDD8" stroke-width="1" fill="none"/></g>`;
   return `<g transform="translate(${x} ${y}) scale(${s})"><defs><radialGradient id="${p}f" cx=".4" cy=".35" r=".75"><stop offset="0" stop-color="#E88E42"/><stop offset=".65" stop-color="#C4641F"/><stop offset="1" stop-color="#94430F"/></radialGradient><linearGradient id="${p}d" x1="0" x2="1"><stop offset="0" stop-color="#CFC6B8"/><stop offset=".45" stop-color="#F6F1E8"/><stop offset="1" stop-color="#BDB3A4"/></linearGradient>${blur(p+'b',4)}</defs>
   <ellipse cx="0" cy="6" rx="${rx+12}" ry="${ry-2}" fill="#000" opacity=".35" filter="url(#${p}b)"/>
   <path d="M${-rx-8} ${cy} L${-rx+2} 2 Q0 ${ry+8} ${rx-2} 2 L${rx+8} ${cy} Q0 ${cy+ry+6} ${-rx-8} ${cy}Z" fill="url(#${p}d)"/>
   ${back}<ellipse cx="0" cy="${cy}" rx="${rx-7}" ry="${ry-6}" fill="url(#${p}f)"/><ellipse cx="-18" cy="${cy-5}" rx="22" ry="4" fill="#FFD8A0" opacity=".35" filter="url(#${p}b)"/>${front}
   ${cream(-26,cy+2,1.15)}${cream(4,cy-6,1.05)}${cream(30,cy+1,1)}</g>`}
  function sauceBowl(x,y,s){const p=fu('sb');const R=40,r=11,top=-34;
   return `<g transform="translate(${x} ${y}) scale(${s})"><defs><radialGradient id="${p}j" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#E23A50"/><stop offset=".6" stop-color="#A4102A"/><stop offset="1" stop-color="#600816"/></radialGradient><linearGradient id="${p}c" x1="0" x2="1"><stop offset="0" stop-color="#C9BFAE"/><stop offset=".45" stop-color="#FBF7EF"/><stop offset="1" stop-color="#B8AD9C"/></linearGradient>${blur(p+'b',3)}</defs>
   <ellipse cx="0" cy="3" rx="${R+6}" ry="7" fill="#000" opacity=".3" filter="url(#${p}b)"/>
   <ellipse cx="0" cy="${top}" rx="${R}" ry="${r}" fill="#EDE6D8"/>
   <ellipse cx="0" cy="${top+1.5}" rx="${R-6}" ry="${r-3.5}" fill="url(#${p}j)"/><ellipse cx="-12" cy="${top-1}" rx="12" ry="2.5" fill="#fff" opacity=".45"/>${berries(10,top+1,4,11)}
   <path d="M${-R} ${top} C${-R+2} ${top+24} ${-R*.5} -2 0 -2 C${R*.5} -2 ${R-2} ${top+24} ${R} ${top} C${R*.6} ${top+r} ${-R*.6} ${top+r} ${-R} ${top}Z" fill="url(#${p}c)"/>
   <path d="M${-R+6} ${top+14} C${-R*.4} ${top+24} ${R*.4} ${top+24} ${R-6} ${top+14}" stroke="#B8302A" stroke-width="3" fill="none"/>
   <path d="M${-R} ${top} C${-R*.6} ${top+r} ${R*.6} ${top+r} ${R} ${top}" stroke="#D8CFBF" stroke-width="2" fill="none"/>
   <path d="M14 ${top} L46 ${top-30}" stroke="#C8C8CC" stroke-width="5" stroke-linecap="round"/><ellipse cx="8" cy="${top+1}" rx="9" ry="3" fill="#B8B8C0"/>
   <rect x="-14" y="-4" width="28" height="5" rx="2" fill="#D8CFBF"/></g>`}
  function mashBowl(x,y,s){const p=fu('mb');const r=rnd(29);let lumps='';[[-30,-42,22],[-8,-50,24],[16,-46,22],[34,-38,18],[0,-62,18],[-18,-58,14],[14,-64,12],[0,-74,10]].forEach(([a,b,c])=>{lumps+=`<ellipse cx="${a}" cy="${b}" rx="${c}" ry="${c*.8}" fill="url(#${p}m)"/>`});
   let specks='';for(let k=0;k<9;k++)specks+=`<circle cx="${f1((r()-.5)*60)}" cy="${f1(-44-r()*28)}" r="0.8" fill="${k%3?'#4A8A2A':'#3A3A2A'}"/>`;
   return `<g transform="translate(${x} ${y}) scale(${s})"><defs><radialGradient id="${p}m" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#FFFDF4"/><stop offset=".7" stop-color="#F0E6CE"/><stop offset="1" stop-color="#C8B894"/></radialGradient><linearGradient id="${p}bw" x1="0" x2="1"><stop offset="0" stop-color="#9AA8C0"/><stop offset=".4" stop-color="#E8EEF6"/><stop offset="1" stop-color="#8A98B0"/></linearGradient>${blur(p+'b',4)}</defs>
   <ellipse cx="0" cy="4" rx="54" ry="10" fill="#000" opacity=".35" filter="url(#${p}b)"/>
   ${lumps}${specks}
   <path d="M-12 -70 l18 -4 l4 12 l-18 5Z" fill="#FFD84A"/><path d="M-12 -70 l18 -4 l2 4 l-18 5Z" fill="#FFF0A0"/><path d="M-4 -60 q2 10 6 12 q4 -4 2 -14Z" fill="#FFE070" opacity=".9"/>
   <path d="M-56 -36 C-54 -10 -30 0 0 0 C30 0 54 -10 56 -36 C30 -28 -30 -28 -56 -36Z" fill="url(#${p}bw)"/><path d="M-56 -36 C-30 -28 30 -28 56 -36" stroke="#5A6A8A" stroke-width="2" fill="none"/><path d="M-50 -24 C-26 -16 26 -16 50 -24" stroke="#3A5A9A" stroke-width="3" fill="none"/>
   <path d="M30 -60 L64 -86" stroke="#C8C8CC" stroke-width="5" stroke-linecap="round"/></g>`}
  function rollBasket(x,y,s){const p=fu('rb');let weave='';for(let k=-4;k<=4;k++)weave+=`<path d="M${k*12} -26 q-4 13 0 26" stroke="#7A5428" stroke-width="2" fill="none"/>`;for(let k=0;k<3;k++)weave+=`<path d="M-54 ${-22+k*9} Q0 ${-14+k*9} 54 ${-22+k*9}" stroke="#8A6234" stroke-width="2" fill="none"/>`;
   const roll=(cx,cy,r2,rot)=>`<g transform="rotate(${rot} ${cx} ${cy})"><ellipse cx="${cx}" cy="${cy}" rx="${r2}" ry="${r2*.72}" fill="url(#${p}r)"/><path d="M${cx-r2*.5} ${cy-r2*.25} q${r2*.5} ${-r2*.25} ${r2} 0" stroke="#8A4A18" stroke-width="2" fill="none" opacity=".7"/><ellipse cx="${cx-r2*.3}" cy="${cy-r2*.38}" rx="${r2*.35}" ry="${r2*.15}" fill="#fff" opacity=".35"/></g>`;
   return `<g transform="translate(${x} ${y}) scale(${s})"><defs><radialGradient id="${p}r" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#F6C878"/><stop offset=".6" stop-color="#D08A3A"/><stop offset="1" stop-color="#8A4A18"/></radialGradient>${blur(p+'b',4)}</defs>
   <ellipse cx="0" cy="4" rx="60" ry="9" fill="#000" opacity=".35" filter="url(#${p}b)"/>
   <path d="M-48 -30 L-70 -14 L-40 -22Z" fill="#C83A2A"/><path d="M48 -30 L72 -12 L42 -22Z" fill="#C83A2A"/><path d="M-58 -16 l8 -8 M62 -14 l-8 -8" stroke="#fff" stroke-width="3"/>
   ${roll(-24,-34,20,-10)}${roll(18,-36,20,8)}${roll(-2,-46,19,0)}${roll(36,-28,16,14)}${roll(-38,-26,15,-16)}
   <path d="M-56 -26 Q0 -16 56 -26 L46 0 Q0 6 -46 0Z" fill="#B88A50"/>${weave}<path d="M-56 -26 Q0 -16 56 -26" stroke="#6A4420" stroke-width="4" fill="none"/></g>`}
  function gravyBoat(x,y,s){return `<g transform="translate(${x} ${y}) scale(${s})"><ellipse cx="0" cy="3" rx="40" ry="6" fill="#000" opacity=".3"/><path d="M-30 -2 h52 l-6 -6 h-40Z" fill="#E8E0D0"/><path d="M-40 -28 C-36 -6 30 -6 40 -24 L56 -34 C44 -26 40 -30 36 -32 Q0 -40 -40 -28Z" fill="#F4EEE2"/><path d="M-40 -28 Q0 -36 40 -30" stroke="#C8BEB0" stroke-width="2" fill="none"/><ellipse cx="-2" cy="-31" rx="34" ry="4" fill="#7A4A22"/><path d="M-40 -24 c-14 -2 -16 16 -2 16" stroke="#F4EEE2" stroke-width="5" fill="none"/><path d="M46 -30 q4 8 2 14" stroke="#7A4A22" stroke-width="3" fill="none" stroke-linecap="round"/></g>`}
  function candlestick(x,by,h){return `<g><path d="M${x-16} ${by} h32 l-4 -8 h-24Z" fill="#C8A050"/><rect x="${x-4}" y="${by-36}" width="8" height="30" fill="#B8903E"/><ellipse cx="${x}" cy="${by-22}" rx="9" ry="4" fill="#D8B060"/><ellipse cx="${x}" cy="${by-38}" rx="12" ry="4" fill="#D8B060"/>${candle(x,by-40,h,18,'cc')}</g>`}
  function tableScene(sc=1){const id='tb'+(RID++);
   let s=`<defs><linearGradient id="${id}cl" x1="0" x2="1"><stop offset="0" stop-color="#CFC2A8"/><stop offset=".5" stop-color="#F2EADB"/><stop offset="1" stop-color="#BEB096"/></linearGradient><radialGradient id="${id}gl"><stop offset="0" stop-color="#FFB050" stop-opacity=".42"/><stop offset="1" stop-color="#FFB050" stop-opacity="0"/></radialGradient><pattern id="${id}pl" width="24" height="24" patternUnits="userSpaceOnUse"><rect width="24" height="24" fill="#B8501E"/><rect width="12" height="24" fill="#A0421A"/><rect width="24" height="6" y="9" fill="#E8B040" opacity=".55"/><rect width="3" height="24" x="16" fill="#5A2A10" opacity=".5"/></pattern></defs>`;
   s+=`<ellipse class="roomglow" cx="0" cy="-400" rx="460" ry="300" fill="url(#${id}gl)"/>`;
   /* legs, then the cloth over the table */
   s+=`<rect x="-262" y="-220" width="24" height="220" fill="#3E2414"/><rect x="238" y="-220" width="24" height="220" fill="#3E2414"/>`;
   let hem=`M-292 -300 L292 -300 L292 -196`;for(let k=0;k<=12;k++){const x=292-k*48.6;hem+=` Q${f1(x-24)} ${k%2?-186:-204} ${f1(x-48.6)} -196`}hem+='Z';
   s+=`<path d="${hem}" fill="url(#${id}cl)"/>`;for(let k=1;k<12;k++)s+=`<path d="M${-292+k*48.6} -296 L${-292+k*48.6} -198" stroke="#A89878" stroke-width="3" opacity=".35"/>`;
   s+=`<path d="M-276 -346 L276 -346 L292 -296 L-292 -296Z" fill="#E8DECA"/><path d="M-276 -346 L276 -346" stroke="#CFC2A8" stroke-width="3"/>`;
   s+=`<path d="M-62 -346 H62 L72 -296 V-170 L36 -156 L0 -170 L-36 -156 L-72 -170 V-296Z" fill="url(#${id}pl)"/><path d="M-72 -296 H72" stroke="#5A2A10" stroke-width="2" opacity=".6"/>`;
   /* back row */
   s+=candlestick(-66,-340,150)+candlestick(118,-340,160);
   s+=rollBasket(-214,-334,.86)+sauceBowl(-118,-334,.86);
   /* front row */
   s+=roastTurkey(10,-300,.92)+pumpkinPie(-206,-294,.92)+mashBowl(222,-294,.92);
   /* leaves and acorns scattered on the cloth, matching the doors */
   s+=leaf(-120,-298,26,70,['#FF8A4A','#D8401E','#7A160E'])+leaf(250,-300,22,-50,['#FFD86E','#E8A12A','#9A5A12'])+acorn(-136,-306,14,{noTwine:true})+acorn(268,-306,13,{noTwine:true});
   /* pumpkins on the floor */
   s+=pumpkin(-320,6,130,{})+pumpkin(316,6,92,{col:'#E9DCC0'})+pumpkin(246,8,58,{plain:true,col:'#E0A030'});
   return `<g class="tscene" transform="scale(${sc})">${s}</g>`}

  /** The two shapes: wide (TVs, computers) and tall (phones) */
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1612, 150, 250, 330], table: [330, 870, 1.02] },
    tall: { w: 390, h: 844, win: null, table: [200, 790, 0.5] },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t) } which poses everything at t seconds.
   * opts: { layout: "wide"|"tall", align: "xMidYMax slice" }
   */
  function build(svg, opts) {
    const L = LAYOUTS[(opts && opts.layout) || "wide"];
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", (opts && opts.align) || "xMidYMax slice");
    const rm = room(L.w, L.h, L.win);
    svg.innerHTML = rm.bg + `<g transform="translate(${L.table[0]} ${L.table[1]})">${tableScene(L.table[2])}</g>` + rm.vig;
    return pose(svg);
  }

  /* gathers every moving part, and returns at(t) */
  function pose(root) {
    const q = (s) => [...root.querySelectorAll(s)];
    const cflames = q(".cflame,.cflame2").map((p, k) => ({ p, x: +p.dataset.x, y: +p.dataset.y, w: +p.dataset.w, h: +p.dataset.h, ph: k * 1.3 }));
    const halos = q(".halo"), rglow = q(".roomglow"), steams = q(".st2");
    const leaves = q(".fleaf").map((g, k) => ({ g, x: +g.dataset.x, y: +g.dataset.y, H: +g.dataset.wh, d: +g.dataset.d, n: 1 + (k % 3), spin: (k % 2 ? 1 : -1) * (1 + (k % 3)) }));
    const walkers = q(".walkers").map((W) => ({ ww: +W.dataset.w, wh: +W.dataset.h, wk: [...W.querySelectorAll(".wk")] }));
    // candle flames flicker with these speeds (whole turns per loop, so the video joins up)
    const s7 = wq(7), s5 = wq(5), s23 = wq(2.3);
    function at(t) {
      cflames.forEach((C) => C.p.setAttribute("d", flameD(C.x, C.y, C.w, C.h * (1 + 0.08 * Math.sin(t * s7 + C.ph)), t, C.ph, 1.4)));
      halos.forEach((h, k) => h.setAttribute("opacity", (0.22 + 0.06 * Math.sin(t * s5 + k)).toFixed(3)));
      rglow.forEach((g) => g.setAttribute("opacity", (0.9 + 0.1 * Math.sin(t * s23)).toFixed(3)));
      steams.forEach((p) => {
        const k = +p.dataset.k; const a = ((t * 6 / LOOP + k / 3) % 1); const x0 = (k - 1) * 40, y0 = -130 - a * 80;
        p.setAttribute("d", `M${f1(x0)} ${f1(y0)} c${f1(10 * Math.sin(t * wq(1) + k))} -14 ${f1(-10 * Math.sin(t * wq(1.3) + k))} -28 ${f1(4 * Math.sin(t * wq(0.7) + k))} -42`);
        p.setAttribute("opacity", (Math.sin(a * Math.PI) * 0.35).toFixed(3));
      });
      leaves.forEach((L) => {
        const span = L.H + 20; const yy = ((L.y + t * span * L.n / LOOP) % span) - 10; const xx = L.x + Math.sin(t * wq(0.9) + L.d) * 12;
        L.g.setAttribute("transform", `translate(${f1(xx)} ${f1(yy)}) rotate(${f1((t * 360 * L.spin / LOOP + L.d * 60) % 360)})`);
      });
      walkers.forEach(({ ww, wh, wk }) => {
        const v = (ww + 360) / 10;
        wk.forEach((g) => {
          const off = +g.dataset.off, sc = 1.6 * (+g.dataset.s); const tt = ((t + LOOP - off) % LOOP); const x = ww + 170 - tt * v;
          const y = groundY(x, ww, wh) + 3; const bob = Math.abs(Math.sin(tt * 7)) * 1.5;
          g.setAttribute("transform", `translate(${f1(x)} ${f1(y - bob)}) scale(${sc})`);
          g.querySelectorAll(".lg").forEach((l) => { const sw = Math.sin(tt * 7 + (+l.dataset.ph) * Math.PI) * 22; l.setAttribute("transform", `rotate(${f1(sw)} ${l.dataset.x} ${l.dataset.p})`); });
          g.querySelectorAll(".arm").forEach((a) => { const r = a.classList.contains("up") ? Math.sin(tt * 3) * 6 : Math.sin(tt * 7) * 18; a.setAttribute("transform", `rotate(${f1(r)} ${a.dataset.px} ${a.dataset.py})`); });
          const hd = g.querySelector(".hd"); if (hd) hd.setAttribute("transform", `translate(${f1(Math.sin(tt * 7) * 3)} 0)`);
          const tl = g.querySelector(".tail"); if (tl) tl.setAttribute("transform", `rotate(${f1(Math.sin(tt * 3.5) * 3)} 10 -36)`);
          const sf = g.querySelector(".scarf"); if (sf) sf.setAttribute("transform", `rotate(${f1(Math.sin(tt * 9) * 8)} 6 -54)`);
          g.querySelectorAll(".st").forEach((p, k) => { const a = ((tt * 0.6 + k / 3) % 1); p.setAttribute("d", `M${-30 + k * 6} ${f1(-78 - a * 14)} c3 -4 -3 -8 0 -12`); p.setAttribute("opacity", (Math.sin(a * Math.PI) * 0.6).toFixed(2)); });
          if (g.dataset.balloon) {
            const b = g.querySelector(".bal"); const sx = Math.sin(tt * 1.4) * 6, sy = Math.sin(tt * 1.1) * 5;
            b.setAttribute("transform", `translate(${f1(sx)} ${f1(sy)}) rotate(${f1(Math.sin(tt * 1.2) * 4)} 0 -150)`);
            g.querySelectorAll(".rope").forEach((r) => { const hx = r.dataset.i === "0" ? -38 : 42; const bx = (r.dataset.i === "0" ? -20 : 20) + sx, by = -112 + sy; r.setAttribute("d", `M${hx} -62 Q${f1((hx + bx) / 2 + 4)} ${f1((-62 + by) / 2 + 10)} ${f1(bx)} ${f1(by)}`); });
          }
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

  G.BenflixFeast = { LOOP, season, greeting, build, play, LAYOUTS };
})(typeof window !== "undefined" ? window : globalThis);
