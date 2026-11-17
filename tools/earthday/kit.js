/*
 * Benflix Earth Day: the drawings from the approved look sheet (2026-11-17, "Design an Earth Day theme for
 * Benflix", sheet 1): a different living thing on each door, the sprout, the flower, the bud and the little Earth.
 * tools/earthday/pictures.js turns them into the PNG pictures the app and the web use. Nothing in Benflix runs
 * this file directly.
 */
(function () {
let UID=0;const u=p=>p+(UID++)+'_';
const hx=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
const toh=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
const mix=(a,b,t)=>{const A=hx(a),B=hx(b);return toh(A.map((v,i)=>v+(B[i]-v)*t))};
const dk=(c,t)=>mix(c,'#000000',t), lt=(c,t)=>mix(c,'#ffffff',t);
const f1=v=>(+v).toFixed(1);
const blur=(id,s)=>`<filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
const GDEF=`<defs><filter id="Gts" x="-10%" y="-40%" width="120%" height="180%"><feDropShadow dx="0" dy="2" stdDeviation="2.5" flood-color="#000" flood-opacity=".55"/></filter></defs>`;
const T=(x,y,s,txt,o={})=>`<text x="${x}" y="${y}" font-family="${o.font||'Inter,sans-serif'}" font-weight="${o.w||600}" font-size="${s}" fill="${o.fill||'#F5F0EA'}" ${o.anchor?`text-anchor="${o.anchor}"`:''} ${o.sh?'filter="url(#Gts)"':''} ${o.op?`opacity="${o.op}"`:''}>${txt}</text>`;
const DOORS=[
 {k:'movies',name:'Movies',sub:'12 new this week',c:'#FFB547',ico:'M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3Z M6.2 5.3l3.1 3.9 M12.4 3.4l3.1 4 M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z'},
 {k:'shows',name:'Shows',sub:'3 new episodes',c:'#7AA7FF',ico:'M4 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z M7 2l5 5 5-5'},
 {k:'live',name:'Live TV',sub:'Parade on at 9',c:'#3DD6C6',ico:'M4.9 19.1C1 15.2 1 8.8 4.9 4.9 M7.8 16.2c-2.3-2.3-2.3-6.1 0-8.5 M16.2 7.8c2.3 2.3 2.3 6.1 0 8.5 M19.1 4.9C23 8.8 23 15.1 19.1 19 M14 12a2 2 0 1 1-4 0 2 2 0 0 1 4 0z'},
 {k:'games',name:'Games',sub:'Pick up where you left',c:'#FF6F91',ico:'M6 11h4 M8 9v4 M15 12h.01 M18 10h.01 M17.32 5H6.68a4 4 0 0 0-3.978 3.59C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258A4 4 0 0 0 17.32 5z'},
 {k:'music',name:'Music',sub:'Cosy mixes ready',c:'#B69CFF',ico:'M9 18V5l12-2v13 M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z M21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z'},
 {k:'books',name:'Books',sub:'Read and listen',c:'#B9E36A',ico:'M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20'}];
const DW=240,DH=220,DR=20;

/* ================= Earth Day objects ================= */
DOORS[0].sub='12 new this week';DOORS[2].sub='Planet marathon at 8';DOORS[4].sub='Spring mixes ready';DOORS[3].sub='Pick up where you left';
function rnd(seed){let a=seed>>>0;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const GOLD=['#FFF1C2','#EBC35A','#9A6E1C'];
function gold(p,c=GOLD,cx=.35,cy=.3){return `<radialGradient id="${p}" cx="${cx}" cy="${cy}" r=".85"><stop offset="0" stop-color="${c[0]}"/><stop offset=".5" stop-color="${c[1]}"/><stop offset="1" stop-color="${c[2]}"/></radialGradient>`}
function lin(p,a,b,x2=0,y2=1){return `<linearGradient id="${p}" x1="0" y1="0" x2="${x2}" y2="${y2}"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient>`}
const glint=(x,y,s,op=1)=>`<path d="M${f1(x)} ${f1(y-s)}Q${f1(x+s*.14)} ${f1(y-s*.14)} ${f1(x+s)} ${f1(y)}Q${f1(x+s*.14)} ${f1(y+s*.14)} ${f1(x)} ${f1(y+s)}Q${f1(x-s*.14)} ${f1(y+s*.14)} ${f1(x-s)} ${f1(y)}Q${f1(x-s*.14)} ${f1(y-s*.14)} ${f1(x)} ${f1(y-s)}Z" fill="#fff" opacity="${op}"/>`;
const sh=(id,s,o=.5)=>`<filter id="${id}" x="-40%" y="-40%" width="180%" height="180%"><feDropShadow dx="0" dy="${f1(s*.6)}" stdDeviation="${s}" flood-color="#000" flood-opacity="${o}"/></filter>`;
/* brass push pin */
function tack(cx,cy,r=6){const p=u('tk');return `<defs>${gold(p)}</defs><circle cx="${cx}" cy="${f1(cy+r*.35)}" r="${r}" fill="#000" opacity=".35"/><circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#${p})" stroke="#5A400C" stroke-width="1.1"/><circle cx="${f1(cx-r*.35)}" cy="${f1(cy-r*.35)}" r="${f1(r*.32)}" fill="#fff" opacity=".85"/>`}
/* jute twine: a light cord with a darker twist pattern */
function twine(d,w=2.6){return `<path d="${d}" stroke="#7A5A30" stroke-width="${f1(w+1.2)}" fill="none" stroke-linecap="round" opacity=".55"/><path d="${d}" stroke="#D8B77E" stroke-width="${w}" fill="none" stroke-linecap="round"/><path d="${d}" stroke="#8C6A3A" stroke-width="${f1(w*.5)}" fill="none" stroke-dasharray="2 3" stroke-linecap="round"/>`}
/* a leaf: tip points along +x. len, wid; type shapes */
function leafPath(len,wid,kind='oval'){const L=len,W=wid/2;
 if(kind==='heart')return `M0 0C${f1(L*.1)} ${f1(-W*1.5)} ${f1(L*.75)} ${f1(-W*1.1)} ${L} 0C${f1(L*.75)} ${f1(W*1.1)} ${f1(L*.1)} ${f1(W*1.5)} 0 0Z`;
 if(kind==='round')return `M0 0C${f1(L*.05)} ${f1(-W*1.3)} ${f1(L*.95)} ${f1(-W*1.3)} ${L} 0C${f1(L*.95)} ${f1(W*1.3)} ${f1(L*.05)} ${f1(W*1.3)} 0 0Z`;
 return `M0 0C${f1(L*.3)} ${f1(-W)} ${f1(L*.7)} ${f1(-W)} ${L} 0C${f1(L*.7)} ${f1(W)} ${f1(L*.3)} ${f1(W)} 0 0Z`}
function leaf(x,y,ang,len,wid,c1,c2,o={}){const p=u('lf');const k=o.kind||'oval';
 let s=`<defs>${lin(p,c1,c2,1,1)}</defs><g transform="translate(${f1(x)} ${f1(y)}) rotate(${f1(ang)})"><path d="${leafPath(len,wid,k)}" fill="url(#${p})" stroke="${dk(c2,.35)}" stroke-width="1"/>`;
 s+=`<path d="M${f1(len*.04)} 0Q${f1(len*.5)} ${f1(-wid*.04)} ${f1(len*.92)} 0" stroke="${lt(c1,.45)}" stroke-width="${f1(Math.max(1,wid*.05))}" fill="none" opacity=".85"/>`;
 if(o.veins!==false)for(let i=1;i<5;i++){const t=i/5.2,xx=len*t;s+=`<path d="M${f1(xx)} 0L${f1(xx+len*.13)} ${f1(-wid*.34*(1-t*.5))}M${f1(xx)} 0L${f1(xx+len*.13)} ${f1(wid*.34*(1-t*.5))}" stroke="${lt(c1,.35)}" stroke-width=".9" opacity=".7"/>`}
 s+=`<path d="M${f1(len*.2)} ${f1(-wid*.28)}Q${f1(len*.5)} ${f1(-wid*.44)} ${f1(len*.76)} ${f1(-wid*.2)}" stroke="#fff" stroke-width="${f1(wid*.08)}" fill="none" opacity=".25" stroke-linecap="round"/></g>`;return s}
const GR=['#9BE070','#3E8C34'],GR2=['#7CC650','#2A6B2A'],GRD=['#5FA848','#1F5422'];

/* ---------- idea 1: a different little living thing on every door ---------- */
/* Movies: terracotta pot with a sprout, in a jute macrame hanger from a pin */
function potCharm(){const p=u('pt');const cx=188,py=6,rimY=64,top=34,bot=24;
 let s=`<defs><linearGradient id="${p}b" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#8E3F22"/><stop offset=".35" stop-color="#D4733F"/><stop offset=".6" stop-color="#C2602F"/><stop offset="1" stop-color="#7A341B"/></linearGradient>${sh(p+'s',3.5)}</defs>`;
 /* back cords */
 s+=twine(`M${cx} ${py+4}L${cx+top-4} ${rimY+2}`,2)+twine(`M${cx} ${py+4}L${cx-top+4} ${rimY+2}`,2);
 /* sprout */
 s+=`<path d="M${cx-2} ${rimY}C${cx-4} ${rimY-14} ${cx+3} ${rimY-24} ${cx} ${rimY-34}" stroke="#4E9A3A" stroke-width="3.4" fill="none" stroke-linecap="round"/>`;
 s+=leaf(cx,rimY-33,-150,30,20,GR[0],GR[1],{kind:'heart'})+leaf(cx,rimY-33,-28,32,21,GR[0],GR[1],{kind:'heart'})+leaf(cx-2,rimY-14,-80,16,10,GR[0],GR[1],{kind:'heart',veins:false});
 /* pot */
 s+=`<g filter="url(#${p}s)"><path d="M${cx-top+5} ${rimY+12}L${cx-bot} ${rimY+50}Q${cx} ${rimY+56} ${cx+bot} ${rimY+50}L${cx+top-5} ${rimY+12}Z" fill="url(#${p}b)" stroke="#5E2812" stroke-width="1.2"/>
 <rect x="${cx-top}" y="${rimY}" width="${top*2}" height="14" rx="3" fill="url(#${p}b)" stroke="#5E2812" stroke-width="1.2"/>
 <ellipse cx="${cx}" cy="${rimY+1.5}" rx="${top-4}" ry="4" fill="#3A2416"/></g>
 <path d="M${cx-top+4} ${rimY+3}H${cx+top-4}" stroke="#F2A273" stroke-width="1.6" opacity=".7"/>
 <path d="M${cx-18} ${rimY+18}Q${cx-16} ${rimY+34} ${cx-12} ${rimY+46}" stroke="#F2A273" stroke-width="3" opacity=".35" fill="none" stroke-linecap="round"/>`;
 /* front cords: down from the rim, knotted into a net under the pot, gathered to a tassel */
 const ty=rimY+70;
 s+=twine(`M${cx} ${py+4}L${cx} ${rimY+2}`,2.2);
 s+=twine(`M${cx-top+4} ${rimY+2}L${cx-top+8} ${rimY+14}L${cx-12} ${rimY+34}L${cx-3} ${ty}`,2)+twine(`M${cx} ${rimY+2}L${cx} ${rimY+14}L${cx-12} ${rimY+34}`,2)+twine(`M${cx} ${rimY+14}L${cx+12} ${rimY+34}`,2)+twine(`M${cx+top-4} ${rimY+2}L${cx+top-8} ${rimY+14}L${cx+12} ${rimY+34}L${cx+3} ${ty}`,2);
 [[cx-top+8,rimY+14],[cx,rimY+14],[cx+top-8,rimY+14],[cx-12,rimY+34],[cx+12,rimY+34]].forEach(([x,y])=>{s+=`<circle cx="${x}" cy="${y}" r="3.4" fill="#C9A36A" stroke="#7A5A30" stroke-width="1"/>`});
 s+=`<ellipse cx="${cx}" cy="${ty}" rx="6" ry="4.5" fill="#C9A36A" stroke="#7A5A30"/>`;
 for(let k=-3;k<=3;k++)s+=`<path d="M${cx+k*1.4} ${ty+3}L${cx+k*2.6} ${ty+22}" stroke="${k%2?'#C9A36A':'#E2C690'}" stroke-width="1.8" stroke-linecap="round"/>`;
 return s+tack(cx,py,6.5)}
/* Shows: a monarch butterfly that has landed on the door */
function wingR(p){const fw=`M2 -3C10 -26 34 -46 60 -44C66 -36 62 -18 50 -5C34 1 16 1 2 0Z`,hw=`M2 2C18 -1 40 4 46 18C49 34 34 46 18 43C8 39 3 22 2 6Z`;
 let s=`<path d="${hw}" fill="url(#${p}o)" stroke="#16100A" stroke-width="4" stroke-linejoin="round"/><path d="${fw}" fill="url(#${p}o)" stroke="#16100A" stroke-width="4.4" stroke-linejoin="round"/>`;
 /* veins */
 s+=`<path d="M4 -2L56 -40M6 -2L50 -24M8 -1L46 -10M6 -4Q24 -30 44 -44M3 4L40 12M3 6L36 28M3 8L20 40M3 4Q28 8 44 26" stroke="#16100A" stroke-width="1.6" fill="none"/>`;
 /* black wing-tip with white spots */
 s+=`<path d="M44 -44C54 -46 64 -42 62 -32C58 -24 54 -16 50 -8C52 -20 50 -32 44 -44Z" fill="#16100A"/>`;
 [[54,-38,1.8],[58,-31,1.6],[56,-23,1.4],[48,-42,1.4],[53,-14,1.3],[40,40,1.4],[30,43,1.3],[44,30,1.3],[46,20,1.2],[20,43,1.2]].forEach(([x,y,r])=>{s+=`<circle cx="${x}" cy="${y}" r="${r}" fill="#fff"/>`});
 [[60,-36,2.2],[61,-27,1.8]].forEach(([x,y,r])=>{s+=`<circle cx="${x}" cy="${y}" r="${r}" fill="#FFC56A"/>`});
 return s}
function butterflyCharm(){const p=u('bf');const cx=180,cy=68;
 let s=`<defs><radialGradient id="${p}o" cx=".2" cy=".5" r=".9"><stop offset="0" stop-color="#FFB13B"/><stop offset=".6" stop-color="#F0801A"/><stop offset="1" stop-color="#C85A0E"/></radialGradient>${sh(p+'s',3,.45)}</defs>`;
 s+=`<g transform="translate(${cx} ${cy}) rotate(-16)" filter="url(#${p}s)"><g transform="scale(-.92 .92)">${wingR(p)}</g><g transform="scale(.92)">${wingR(p)}</g>
 <ellipse cx="0" cy="8" rx="3.6" ry="20" fill="#1C140C"/><circle cx="0" cy="-14" r="5" fill="#1C140C"/>${[[-1.2,0],[1.2,4],[-1.2,8],[1.2,12],[-1,16]].map(([x,y])=>`<circle cx="${x}" cy="${y}" r=".9" fill="#fff"/>`).join('')}
 <path d="M-2 -18Q-8 -34 -14 -40M2 -18Q8 -34 14 -40" stroke="#1C140C" stroke-width="1.6" fill="none"/><circle cx="-14" cy="-40" r="2.4" fill="#1C140C"/><circle cx="14" cy="-40" r="2.4" fill="#1C140C"/></g>`;
 return s}
/* Live TV: a hanging glass globe terrarium (moss, pebbles and a tiny fern) */
function orbCharm(){const p=u('ob');const cx=186,cy=86,r=44,py=6;
 let s=`<defs><clipPath id="${p}c"><circle cx="${cx}" cy="${cy}" r="${r-2}"/></clipPath><radialGradient id="${p}g" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#fff" stop-opacity=".22"/><stop offset=".7" stop-color="#CDEBF2" stop-opacity=".08"/><stop offset="1" stop-color="#9FD3E0" stop-opacity=".3"/></radialGradient>${lin(p+'m','#7CCB55','#2E7A2C')}${sh(p+'s',3,.4)}</defs>`;
 s+=twine(`M${cx} ${py+4}L${cx} ${cy-r-6}`,2.4)+`<path d="M${cx-7} ${cy-r-4}Q${cx} ${cy-r-14} ${cx+7} ${cy-r-4}" stroke="#D8B77E" stroke-width="2.4" fill="none"/>`;
 s+=`<g filter="url(#${p}s)"><circle cx="${cx}" cy="${cy}" r="${r}" fill="#0E2A30" opacity=".35"/></g>`;
 /* inside */
 const R=rnd(5);let inner=`<rect x="${cx-r}" y="${cy+16}" width="${r*2}" height="${r}" fill="#C9B48A"/><path d="M${cx-r} ${cy+24}Q${cx} ${cy+18} ${cx+r} ${cy+24}V${cy+r}H${cx-r}Z" fill="#8E7350"/>`;
 for(let k=0;k<14;k++)inner+=`<ellipse cx="${f1(cx-r+6+R()*(r*2-12))}" cy="${f1(cy+26+R()*14)}" rx="${f1(3+R()*3)}" ry="${f1(2+R()*2)}" fill="${['#E7E2D6','#A9A39A','#6E6A64','#D8C7A6'][k%4]}"/>`;
 inner+=`<path d="M${cx-r} ${cy+18}C${cx-26} ${cy+2} ${cx-10} ${cy+4} ${cx-2} ${cy+12}C${cx+8} ${cy+2} ${cx+24} ${cy+4} ${cx+r} ${cy+16}V${cy+24}H${cx-r}Z" fill="url(#${p}m)"/>`;
 for(let k=0;k<26;k++)inner+=`<circle cx="${f1(cx-r+8+R()*(r*2-16))}" cy="${f1(cy+10+R()*10)}" r="${f1(1.2+R()*1.8)}" fill="${R()<.5?'#9BE070':'#3E8C34'}"/>`;
 /* tiny fern */
 const fx=cx-8,fy=cy+10;inner+=`<path d="M${fx} ${fy}Q${fx+2} ${fy-20} ${fx+12} ${fy-34}" stroke="#3E8C34" stroke-width="2" fill="none"/>`;
 for(let i=0;i<7;i++){const t=i/7,x=fx+2*t+10*t*t,y=fy-34*t;const l=12*(1-t*.6);inner+=leaf(x,y,-160+t*10,l,5,GR[0],GR[1],{veins:false})+leaf(x,y,-20-t*10,l,5,GR[0],GR[1],{veins:false})}
 inner+=`<path d="M${cx+14} ${fy+2}Q${cx+18} ${fy-12} ${cx+24} ${fy-16}" stroke="#5FA848" stroke-width="2.4" fill="none"/>`+leaf(cx+24,fy-16,-40,12,8,GR[0],GR[1],{kind:'round',veins:false})+leaf(cx+16,fy-6,200,10,7,GR[0],GR[1],{kind:'round',veins:false});
 s+=`<g clip-path="url(#${p}c)">${inner}</g>`;
 /* glass */
 s+=`<circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#${p}g)" stroke="#E6F6FA" stroke-opacity=".75" stroke-width="2"/>
 <ellipse cx="${cx+14}" cy="${cy-10}" rx="18" ry="16" fill="none" stroke="#E6F6FA" stroke-opacity=".8" stroke-width="2.2"/>
 <path d="M${cx-r+8} ${cy-6}A${r-8} ${r-8} 0 0 1 ${cx-6} ${cy-r+8}" stroke="#fff" stroke-width="5" fill="none" opacity=".6" stroke-linecap="round"/>
 <circle cx="${cx}" cy="${cy-r}" r="5" fill="#CDEBF2" fill-opacity=".5" stroke="#E6F6FA" stroke-width="1.6"/>`+glint(cx-r*.5,cy-r*.55,5,.9);
 return s+tack(cx,py,6.5)}
/* Games: a ladybird walking on a big leaf, the leaf stalk held by a tack */
function ladybird(x,y,ang,r){const p=u('lb');return `<defs><radialGradient id="${p}" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#FF6A55"/><stop offset=".6" stop-color="#E0281E"/><stop offset="1" stop-color="#8E1008"/></radialGradient></defs><g transform="translate(${f1(x)} ${f1(y)}) rotate(${f1(ang)})">
 ${[-1,1].map(sd=>[-.5,0,.5].map(t=>`<path d="M${f1(t*r)} ${f1(sd*r*.6)}l${f1(t*r*.5)} ${f1(sd*r*.55)}" stroke="#14100C" stroke-width="2" stroke-linecap="round"/>`).join('')).join('')}
 <ellipse cx="${f1(r*.95)}" cy="0" rx="${f1(r*.42)}" ry="${f1(r*.5)}" fill="#14100C"/><circle cx="${f1(r*1.12)}" cy="${f1(-r*.25)}" r="${f1(r*.1)}" fill="#fff"/><circle cx="${f1(r*1.12)}" cy="${f1(r*.25)}" r="${f1(r*.1)}" fill="#fff"/>
 <path d="M${f1(r*1.3)} ${f1(-r*.2)}q${f1(r*.3)} ${f1(-r*.2)} ${f1(r*.4)} ${f1(-r*.5)}M${f1(r*1.3)} ${f1(r*.2)}q${f1(r*.3)} ${f1(r*.2)} ${f1(r*.4)} ${f1(r*.5)}" stroke="#14100C" stroke-width="1.4" fill="none"/>
 <ellipse cx="0" cy="0" rx="${r}" ry="${f1(r*.86)}" fill="url(#${p})" stroke="#5A0A04" stroke-width="1"/><path d="M${f1(r*.7)} 0H${f1(-r)}" stroke="#14100C" stroke-width="1.8"/>
 ${[[.35,-.42,.2],[.35,.42,.2],[-.25,-.5,.22],[-.25,.5,.22],[-.62,-.2,.16],[-.62,.2,.16]].map(([a,b,c])=>`<circle cx="${f1(a*r)}" cy="${f1(b*r)}" r="${f1(c*r)}" fill="#14100C"/>`).join('')}
 <ellipse cx="${f1(r*.1)}" cy="${f1(-r*.42)}" rx="${f1(r*.4)}" ry="${f1(r*.15)}" fill="#fff" opacity=".5"/></g>`}
function ladyCharm(){const p=u('lc');const tx=210,ty=14;
 let s=`<defs>${sh(p+'s',3,.45)}</defs><path d="M${tx} ${ty}Q${tx-6} ${ty+12} ${tx-14} ${ty+18}" stroke="#4E8A30" stroke-width="3.4" fill="none" stroke-linecap="round"/>`;
 s+=`<g filter="url(#${p}s)">${leaf(tx-14,ty+18,128,112,66,GR2[0],GR2[1],{kind:'heart'})}</g>`;
 s+=ladybird(168,72,128+180,15);
 s+=`<circle cx="148" cy="98" r="5.5" fill="#E8FAFF" fill-opacity=".45" stroke="#fff" stroke-opacity=".8" stroke-width="1"/><circle cx="146.5" cy="96.5" r="1.6" fill="#fff"/>`;
 return s+tack(tx,ty,6.5)}
/* Music: a robin singing on a twig that is tied to two tacks */
function robin(x,y,s){const p=u('rb');return `<defs>${lin(p+'b','#8A6E58','#4E3A2C')}<radialGradient id="${p}r" cx=".4" cy=".4" r=".7"><stop offset="0" stop-color="#FF9A4A"/><stop offset="1" stop-color="#D45A1C"/></radialGradient></defs><g transform="translate(${x} ${y}) scale(${s})">
 <path d="M-30 -6L-52 6L-50 12L-26 4Z" fill="#4E3A2C"/>
 <path d="M-30 -10C-28 -32 -6 -40 10 -34C22 -30 26 -18 22 -6C18 10 2 16 -12 14C-24 12 -32 4 -30 -10Z" fill="url(#${p}b)"/>
 <path d="M6 -30C20 -28 26 -16 22 -4C18 10 4 15 -8 13C-2 4 0 -12 6 -30Z" fill="url(#${p}r)"/>
 <path d="M-24 -14C-16 -22 -4 -22 4 -14C-4 -6 -16 -4 -24 -14Z" fill="#5E4636" stroke="#3A2A1E" stroke-width=".8"/><path d="M-22 -12L-6 -12M-20 -9L-8 -9" stroke="#3A2A1E" stroke-width=".8"/>
 <circle cx="12" cy="-26" r="3.2" fill="#0E0A08"/><circle cx="13" cy="-27" r="1" fill="#fff"/>
 <path d="M20 -26L32 -32L22 -22Z" fill="#E8C060" stroke="#7A5A20" stroke-width=".6"/><path d="M20 -23L31 -20L21 -20Z" fill="#C9A040" stroke="#7A5A20" stroke-width=".6"/>
 <path d="M-4 12L-6 22M4 11L4 22" stroke="#7A5A40" stroke-width="2" stroke-linecap="round"/><path d="M-10 22L-2 22M0 22L8 22" stroke="#7A5A40" stroke-width="2" stroke-linecap="round"/></g>`}
function robinCharm(){const p=u('rc');const x1=138,y1=104,x2=232,y2=88;
 let s=`<defs>${lin(p+'t','#8A6440','#4A321C')}${sh(p+'s',3,.45)}</defs><g filter="url(#${p}s)"><path d="M${x1-6} ${y1+3}Q${(x1+x2)/2} ${y1-8} ${x2+4} ${y2-2}L${x2+4} ${y2+4}Q${(x1+x2)/2} ${y1-1} ${x1-6} ${y1+8}Z" fill="url(#${p}t)"/>
 <path d="M168 99Q160 84 150 80" stroke="#6A4A2C" stroke-width="3" fill="none" stroke-linecap="round"/></g>`+leaf(150,80,-150,20,11,GR[0],GR[1])+leaf(214,92,-60,18,10,GR[0],GR[1])+leaf(214,92,10,16,9,GR[0],GR[1]);
 s+=`<g filter="url(#${p}s)">${robin(190,74,1.05)}</g>`;
 s+=twine(`M${x1} ${y1-6}L${x1+2} ${y1+10}M${x1-4} ${y1-4}L${x1+6} ${y1+8}`,1.8)+twine(`M${x2-2} ${y2-6}L${x2} ${y2+10}M${x2-6} ${y2-4}L${x2+4} ${y2+8}`,1.8);
 return s+tack(x1,y1-8,5.5)+tack(x2,y2-8,5.5)}
/* Books: a pressed daisy and a fern frond under a strip of washi tape */
function daisy(cx,cy,R,rot){let s=`<g transform="rotate(${rot} ${cx} ${cy})">`;const N=18;
 for(let k=0;k<N;k++){const a=k/N*360;s+=`<g transform="rotate(${f1(a)} ${cx} ${cy})"><path d="M${cx} ${cy}C${cx+R*.3} ${f1(cy-R*.16)} ${cx+R*.9} ${f1(cy-R*.14)} ${cx+R} ${cy}C${cx+R*.9} ${f1(cy+R*.14)} ${cx+R*.3} ${f1(cy+R*.16)} ${cx} ${cy}Z" fill="${k%2?'#FFFFFF':'#F2EEE6'}" stroke="#CFC8BA" stroke-width=".7"/></g>`}
 const R2=rnd(7);let dots='';for(let k=0;k<30;k++){const a=R2()*6.28,rr=Math.sqrt(R2())*R*.28;dots+=`<circle cx="${f1(cx+Math.cos(a)*rr)}" cy="${f1(cy+Math.sin(a)*rr)}" r="1.1" fill="#B9780E"/>`}
 return s+`<circle cx="${cx}" cy="${cy}" r="${f1(R*.32)}" fill="#F6C232" stroke="#C98E10" stroke-width="1"/>${dots}</g>`}
function pressedCharm(){const p=u('pr');
 let s=`<defs>${sh(p+'s',1.4,.4)}<pattern id="${p}t" width="10" height="10" patternUnits="userSpaceOnUse"><rect width="10" height="10" fill="#EBD9B4"/><circle cx="5" cy="5" r="1.8" fill="#6FAF5A"/></pattern></defs><g filter="url(#${p}s)">`;
 /* fern frond */
 const fx=226,fy=132,tx=170,ty=12;s+=`<path d="M${fx} ${fy}Q${fx-14} ${(fy+ty)/2} ${tx} ${ty}" stroke="#3E7A30" stroke-width="3" fill="none"/>`;
 for(let i=1;i<14;i++){const t=i/14,x=(1-t)*(1-t)*fx+2*(1-t)*t*(fx-14)+t*t*tx,y=(1-t)*(1-t)*fy+2*(1-t)*t*((fy+ty)/2)+t*t*ty;const l=26*(1-t*.7);const ang=-96-26*t;s+=leaf(x,y,ang-58,l,9,GRD[0],GRD[1],{veins:false})+leaf(x,y,ang+58,l,9,GRD[0],GRD[1],{veins:false})}
 /* daisy and stem */
 s+=`<path d="M184 62Q176 100 150 138" stroke="#5A9A3E" stroke-width="2.6" fill="none"/>`+leaf(168,104,200,22,9,GR[0],GR[1],{veins:false})+leaf(162,116,-30,20,8,GR[0],GR[1],{veins:false});
 s+=daisy(184,58,32,8)+`</g>`;
 /* tape over the stems */
 s+=`<g transform="rotate(-24 186 120)"><path d="M150 110L224 110L222 114L225 118L222 122L225 126L222 130L150 130L152 126L149 122L152 118L149 114Z" fill="url(#${p}t)" opacity=".9"/><path d="M150 110H224" stroke="#fff" stroke-opacity=".4"/></g>`;
 return s}
const CHARM={movies:potCharm,shows:butterflyCharm,live:orbCharm,games:ladyCharm,music:robinCharm,books:pressedCharm};

/* ---------- idea 2: a different plant growing over the top edge of every door ---------- */
function vinePts(x0,y0,x1,y1,sag){return t=>[x0+(x1-x0)*t,y0+(y1-y0)*t+Math.sin(t*Math.PI)*sag]}
function ivy(x,y,ang,s){const p=u('iv');return `<defs>${lin(p,'#4E9A3A','#1E5222',1,1)}</defs><g transform="translate(${f1(x)} ${f1(y)}) rotate(${f1(ang)}) scale(${s})"><path d="M0 0L-12 -6L-22 -2L-16 4L-26 12L-14 14L-12 24L-2 16L0 28L2 16L12 24L14 14L26 12L16 4L22 -2L12 -6Z" transform="translate(0 0)" fill="url(#${p})" stroke="#16401A" stroke-width="1"/><path d="M0 0V24M0 6L-18 0M0 6L18 0M0 10L-18 12M0 10L18 12" stroke="#B8E0A0" stroke-width="1.1" opacity=".8"/></g>`}
function grow(d){const k=d.k;let s='';const R=rnd(k.length*7+3);
 if(k==='movies'){/* ivy: tendrils over the top edge, one trailing down the right side */
  s+=`<path d="M108 -2C140 10 170 4 200 10C222 14 230 30 236 60C240 90 232 112 236 130" stroke="#5A4A2A" stroke-width="3" fill="none"/>`;
  [[112,4,10,.9],[134,10,-20,1],[158,8,15,1.1],[182,10,-10,1],[204,14,20,1.1],[226,26,-30,1],[234,52,10,1.1],[230,80,-20,1],[236,106,15,.9],[148,20,40,.8],[214,40,-60,.85],[228,128,0,.8]].forEach(([x,y,a,sc])=>s+=ivy(x,y,a,sc))}
 if(k==='shows'){/* fern fronds hanging over the top edge */
  [[128,0,104,.75],[158,0,82,1],[192,0,96,1.12],[222,0,72,.95],[240,10,110,.8]].forEach(([x,y,a,L])=>{const l=100*L;const ex=x+Math.cos(a*Math.PI/180)*l*.4,ey=y+l;s+=`<path d="M${x} ${y-4}Q${x+8} ${y+l*.4} ${f1(ex)} ${f1(ey)}" stroke="#3E7A30" stroke-width="2.4" fill="none"/>`;
   for(let i=1;i<12;i++){const t=i/12;const qx=(1-t)*(1-t)*x+2*(1-t)*t*(x+8)+t*t*ex,qy=(1-t)*(1-t)*(y-4)+2*(1-t)*t*(y+l*.4)+t*t*ey;const ll=18*L*(1-t*.6);s+=leaf(qx,qy,180-30+t*10,ll,6,GR2[0],GR2[1],{veins:false})+leaf(qx,qy,30-t*10,ll,6,GR2[0],GR2[1],{veins:false})}})}
 if(k==='live'){/* morning glory: heart leaves and blue trumpet flowers */
  s+=`<path d="M104 -2C130 14 150 6 176 14C200 22 214 10 236 26C246 50 228 70 236 100" stroke="#4E8A30" stroke-width="2.6" fill="none"/>`;
  [[116,6,80],[146,10,110],[166,14,60],[196,20,100],[222,16,70],[236,42,140],[232,72,40],[236,96,120]].forEach(([x,y,a])=>s+=leaf(x,y,a,30,24,GR[0],GR[1],{kind:'heart'}));
  const mg=(cx,cy,r,rot)=>{const p=u('mg');return `<defs><radialGradient id="${p}"><stop offset="0" stop-color="#FFFFFF"/><stop offset=".22" stop-color="#FFF6C8"/><stop offset=".36" stop-color="#7FC2FF"/><stop offset="1" stop-color="#2D6BE0"/></radialGradient></defs><g transform="rotate(${rot} ${cx} ${cy})"><path d="M${cx-r*.2} ${cy+r*.7}L${cx} ${cy+r*1.5}L${cx+r*.2} ${cy+r*.7}Z" fill="#9FD0FF"/>${Array.from({length:5},(_,i)=>{const a=i/5*Math.PI*2-Math.PI/2;return `<path d="M${cx} ${cy}L${f1(cx+Math.cos(a-.6)*r)} ${f1(cy+Math.sin(a-.6)*r)}Q${f1(cx+Math.cos(a)*r*1.12)} ${f1(cy+Math.sin(a)*r*1.12)} ${f1(cx+Math.cos(a+.6)*r)} ${f1(cy+Math.sin(a+.6)*r)}Z" fill="url(#${p})" stroke="#1E4FB0" stroke-width=".8"/><path d="M${cx} ${cy}L${f1(cx+Math.cos(a)*r)} ${f1(cy+Math.sin(a)*r)}" stroke="#fff" stroke-opacity=".6" stroke-width="1.2"/>`}).join('')}<circle cx="${cx}" cy="${cy}" r="${f1(r*.16)}" fill="#FFE98A"/></g>`};
  s+=mg(152,30,22,10)+mg(210,40,25,-8)+mg(226,96,18,20)}
 if(k==='games'){/* nasturtium: round lily-pad leaves and orange flowers */
  s+=`<path d="M106 -2C136 16 164 4 190 16C216 28 230 16 238 40" stroke="#5E9A3A" stroke-width="2.6" fill="none"/>`;
  const nl=(cx,cy,r)=>{const p=u('na');let o=`<defs><radialGradient id="${p}" cx=".5" cy=".5" r=".6"><stop offset="0" stop-color="#9AD36A"/><stop offset="1" stop-color="#4E8E34"/></radialGradient></defs><circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#${p})" stroke="#2E5E22" stroke-width="1"/>`;for(let i=0;i<9;i++){const a=i/9*Math.PI*2;o+=`<path d="M${cx} ${cy}L${f1(cx+Math.cos(a)*r*.92)} ${f1(cy+Math.sin(a)*r*.92)}" stroke="#D2F0B0" stroke-width="1" opacity=".8"/>`}return o+`<circle cx="${cx}" cy="${cy}" r="2" fill="#D2F0B0"/>`};
  const nf=(cx,cy,r,rot,c)=>{let o=`<g transform="rotate(${rot} ${cx} ${cy})">`;for(let i=0;i<5;i++){const a=i/5*Math.PI*2-Math.PI/2;o+=`<ellipse cx="${f1(cx+Math.cos(a)*r*.55)}" cy="${f1(cy+Math.sin(a)*r*.55)}" rx="${f1(r*.55)}" ry="${f1(r*.42)}" transform="rotate(${f1(a*180/Math.PI)} ${f1(cx+Math.cos(a)*r*.55)} ${f1(cy+Math.sin(a)*r*.55)})" fill="${c}" stroke="${dk(c,.3)}" stroke-width=".8"/>`}return o+`<circle cx="${cx}" cy="${cy}" r="${f1(r*.22)}" fill="#7A2A08"/>${[0,1,2,3,4].map(i=>{const a=i/5*Math.PI*2-Math.PI/2;return `<path d="M${cx} ${cy}L${f1(cx+Math.cos(a)*r*.6)} ${f1(cy+Math.sin(a)*r*.6)}" stroke="#7A2A08" stroke-width="1.2"/>`}).join('')}</g>`};
  [[120,8,15],[150,24,19],[178,10,16],[204,30,21],[230,14,15],[232,52,18]].forEach(([x,y,r])=>s+=nl(x,y,r));
  s+=nf(166,46,17,10,'#FF8A1C')+nf(218,64,19,-12,'#FFC12E')+nf(192,4,13,20,'#E8461C')}
 if(k==='music'){/* sweet pea: pea leaves, curly tendrils, pink and violet flowers */
  s+=`<path d="M104 -2C132 12 160 2 190 12C214 20 228 12 238 30" stroke="#6AA84A" stroke-width="2.4" fill="none"/>`;
  [[126,6,70],[146,6,120],[176,10,60],[204,16,110],[226,18,70]].forEach(([x,y,a])=>s+=leaf(x,y,a,24,12,GR[0],GR[1],{veins:false})+leaf(x,y,a+40,20,10,GR[0],GR[1],{veins:false}));
  [[134,8],[192,14],[234,28]].forEach(([x,y])=>{s+=`<path d="M${x} ${y}c8 8 2 20 -6 16c-6 -3 0 -10 4 -5c3 4 -2 8 -4 6" stroke="#8CC86A" stroke-width="1.8" fill="none" stroke-linecap="round"/>`});
  const sp=(cx,cy,s1,c)=>`<g transform="translate(${cx} ${cy}) scale(${s1})"><path d="M0 4L0 18" stroke="#6AA84A" stroke-width="2.4"/><path d="M0 2C-20 2 -22 -22 -10 -26C-6 -30 6 -30 10 -26C22 -22 20 2 0 2Z" fill="${c}" stroke="${dk(c,.35)}" stroke-width="1"/><path d="M-12 -18Q0 -24 12 -18M-14 -8Q0 -14 14 -8" stroke="${lt(c,.35)}" stroke-width="1.4" fill="none"/><path d="M0 6C-12 6 -16 -6 -8 -10C-4 -12 0 -8 0 -4C0 -8 4 -12 8 -10C16 -6 12 6 0 6Z" fill="${dk(c,.12)}" stroke="${dk(c,.4)}" stroke-width=".9"/><path d="M-3 6Q0 -2 3 6Z" fill="#F6EEDC"/></g>`;
  s+=sp(156,46,1,'#FF7BAC')+sp(194,58,1.15,'#B48AF0')+sp(226,58,1,'#FF5F8F')+sp(134,34,.85,'#F2A6E0')}
 if(k==='books'){/* wisteria: purple flower clusters hanging down from the top edge */
  s+=`<path d="M100 -2C130 6 160 2 190 6C214 10 230 4 240 8" stroke="#7A5A34" stroke-width="3" fill="none"/>`;
  [[118,4,60],[140,2,120],[166,4,70],[196,6,110],[222,6,60]].forEach(([x,y,a])=>s+=leaf(x,y,a,26,10,GR[0],GR[1],{veins:false}));
  [[132,4,70],[160,4,96],[188,6,84],[214,6,104],[234,6,66]].forEach(([x,y,L],j)=>{for(let i=0;i<16;i++){const t=i/16,yy=y+6+t*L,w=(1-t)*13+3;for(let q=-1;q<=1;q+=2){const xx=x+q*w*.5*(.6+R()*.4)+Math.sin(t*4+j)*2;s+=`<ellipse cx="${f1(xx)}" cy="${f1(yy)}" rx="${f1(4.6*(1-t*.45))}" ry="${f1(3.6*(1-t*.45))}" fill="${mix('#C9A8FF','#7A4FD0',t*.8+R()*.2)}" stroke="#4A2E8A" stroke-width=".5"/>`}}})}
 return s}

/* ---------- idea 3: recycled paper cut-outs on a twine line ---------- */
function paperDefs(p){return `<filter id="${p}r" x="-10%" y="-10%" width="120%" height="120%"><feTurbulence type="fractalNoise" baseFrequency=".09" numOctaves="2" seed="4" result="n"/><feDisplacementMap in="SourceGraphic" in2="n" scale="3.4"/></filter><pattern id="${p}k" width="60" height="60" patternUnits="userSpaceOnUse"><rect width="60" height="60" fill="#C9A274"/><path d="M4 10l14 3M30 6l9 -2M10 40l20 4M44 30l12 2M20 52l8 -3M48 50l6 5" stroke="#A47E52" stroke-width="1"/><path d="M14 24l10 1M38 44l8 -1" stroke="#E2C49A" stroke-width="1.2"/></pattern>${sh(p+'s',2.6,.45)}`}
function clothespin(x,y,rot=0){return `<g transform="rotate(${rot} ${x} ${y})"><rect x="${x-5}" y="${y-10}" width="10" height="30" rx="2" fill="#E4C79A" stroke="#8A6A3A" stroke-width="1"/><path d="M${x} ${y-10}V${y+20}" stroke="#8A6A3A" stroke-width="1"/><rect x="${x-6}" y="${y+1}" width="12" height="4" rx="1" fill="#9AA0A6" stroke="#5A6066" stroke-width=".8"/></g>`}
function cutout(k,p){const K=`url(#${p}k)`,G='#5DAF4E',GD='#3E8A36';
 if(k==='movies')return `<path d="M-6 74H6L5 44H-5Z" fill="${K}"/><path d="M0 -6C14 -6 22 6 20 16C32 18 34 34 26 40C30 52 18 60 8 54C2 62 -12 60 -14 52C-28 54 -34 40 -26 32C-34 22 -24 10 -14 12C-14 2 -8 -6 0 -6Z" fill="${G}"/><path d="M-10 18C-2 14 8 16 12 24M-16 36C-6 32 4 36 8 42" stroke="${GD}" stroke-width="2" fill="none"/>`;
 if(k==='shows')return `<circle cx="18" cy="10" r="14" fill="#F2C442"/><path d="M-36 66L-8 16L6 38L18 22L40 66Z" fill="${K}"/><path d="M-8 16L-16 30L-10 28L-4 34L0 26L4 32L6 38Z" fill="#F4EEE2"/><path d="M-36 66C-20 58 -6 62 6 56C20 50 30 58 40 56V66Z" fill="${G}"/>`;
 if(k==='live')return `<circle cx="0" cy="30" r="34" fill="#3F8FD2"/><path d="M-22 6C-12 0 -2 6 -6 14C-10 20 -2 26 -8 32C-14 40 -24 34 -26 24C-28 16 -30 10 -22 6ZM6 10C16 6 26 14 24 22C22 30 14 28 12 36C10 46 2 52 -2 44C-4 36 4 32 2 24C0 18 0 12 6 10ZM-18 50C-12 46 -4 52 -8 58C-12 62 -20 58 -18 50Z" fill="${G}"/><path d="M-30 18C-18 26 18 26 32 16" stroke="#fff" stroke-opacity=".35" stroke-width="2" fill="none"/>`;
 if(k==='games')return `<path d="M-30 30C-30 14 -16 6 -4 8C6 2 16 6 20 14L38 6L32 20L40 26L24 28C18 40 2 46 -12 42C-22 40 -30 36 -30 30Z" fill="${K}"/><path d="M-8 22C0 10 16 10 22 18C14 30 2 32 -8 22Z" fill="${G}"/><path d="M-6 22C2 16 12 16 18 20" stroke="${GD}" stroke-width="1.6" fill="none"/><circle cx="-18" cy="20" r="2.8" fill="#2A2018"/><path d="M-30 24L-42 28L-30 31Z" fill="#E8A23A"/><path d="M-14 40L-16 50M-6 42L-6 52" stroke="#5A4028" stroke-width="2"/>`;
 if(k==='music')return `<path d="M0 30V76" stroke="${GD}" stroke-width="4"/><path d="M0 60C10 50 22 52 24 58C14 64 6 64 0 60Z" fill="${G}"/>${[0,1,2,3,4,5].map(i=>`<ellipse cx="0" cy="12" rx="10" ry="20" transform="rotate(${i*60} 0 30)" fill="${i%2?'#F07AA0':'#E85A86'}"/>`).join('')}<circle cx="0" cy="30" r="9" fill="#F6C232"/>`;
 return `<path d="M0 -6C24 4 30 36 0 72C-30 36 -24 4 0 -6Z" fill="${G}"/><path d="M0 -2V76M0 20L-14 10M0 20L14 10M0 36L-16 26M0 36L16 26M0 52L-12 44M0 52L12 44" stroke="${GD}" stroke-width="2" fill="none"/>`}
function paper(d){const p=u('pp');const x1=118,x2=234,y=10,cx=182;
 let s=`<defs>${paperDefs(p)}</defs>`+twine(`M${x1} ${y}Q${cx} ${y+30} ${x2} ${y}`,2.2);
 s+=`<g transform="translate(${cx} 40) rotate(${d.k.length%2?-5:5})" filter="url(#${p}s)"><g filter="url(#${p}r)"><g transform="translate(0 6)">${cutout(d.k,p)}</g></g></g>`;
 s+=clothespin(cx,24,d.k.length%2?-5:5);
 return s+tack(x1,y,5.5)+tack(x2,y,5.5)}

const MOTIF={life:(d)=>CHARM[d.k](),grow:(d)=>grow(d),paper:(d)=>paper(d)};
let MOT='life';

function door(d,motif,o={}){const p=u('d');const c=d.c;
 let s=`<defs><linearGradient id="${p}f" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${lt(c,.1)}"/><stop offset="1" stop-color="${dk(c,.16)}"/></linearGradient><clipPath id="${p}k"><rect width="${DW}" height="${DH}" rx="${DR}"/></clipPath>${blur(p+'fr',6)}</defs>`;
 s+=`<rect y="6" width="${DW}" height="${DH}" rx="${DR}" fill="#000" opacity=".35"/>`;
 s+=`<rect width="${DW}" height="${DH}" rx="${DR}" fill="url(#${p}f)"/><rect x="1.5" y="1.5" width="${DW-3}" height="${DH-3}" rx="${DR-1}" fill="none" stroke="#fff" stroke-opacity=".18" stroke-width="2"/>`;
 s+=`<defs><linearGradient id="${p}sh" x1="0" y1="0" x2="0" y2="1"><stop offset=".45" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".22"/></linearGradient></defs><rect width="${DW}" height="${DH}" rx="${DR}" fill="url(#${p}sh)"/>`;
 s+=`<g transform="translate(20 20) scale(1.5)" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="${d.ico}"/></g>`;
 if(motif)s+=`<g ${motif==='grow'?`clip-path="url(#${p}k)"`:''}>${MOTIF[motif](d)}</g>`;
 s+=T(20,180,31,d.name,{font:'Outfit,sans-serif',w:800,sh:true})+(o.sub===false?'':T(20,204,15,d.sub,{w:600,sh:true,op:.95}));
 if(o.focus)s+=`<rect x="-8" y="-8" width="${DW+16}" height="${DH+16}" rx="${DR+7}" fill="none" stroke="#fff" stroke-width="12" opacity=".22" filter="url(#${p}fr)"/><rect x="-7" y="-7" width="${DW+14}" height="${DH+14}" rx="${DR+6}" fill="none" stroke="#fff" stroke-width="4.5"/>`;
 return `<g>${s}</g>`}
const doorAt=(d,m,x,y,sc,o)=>`<g transform="translate(${x} ${y}) scale(${sc})">${door(d,m,o)}</g>`;
function doorRow(m){let s='';DOORS.forEach((d,i)=>{s+=doorAt(d,m,20+(i%3)*270,24+Math.floor(i/3)*262,1,{focus:i===0})});return `<svg class="g" viewBox="0 0 830 520">${GDEF}${s}</svg>`}
function bigDoor(k){const d=DOORS.find(x=>x.k===k);return `<svg class="g" viewBox="-20 -20 280 270">${GDEF}${door(d,'life')}</svg>`}

/* ---------- colours and the small Earth Day touches ---------- */
const PAL={
 leaf:{A:'#7CD36B',AT:'#0B2410',B:'#1E5A86',PANEL:'#0B1A26',PLINE:'#1E3A50',name:'Leaf green and ocean blue',what:'Selected things are fresh leaf green with dark text. Other buttons, switches and panels are deep ocean blue. Land and sea, the two colours of the planet from space.'},
 sky:{A:'#56BDEB',AT:'#05202E',B:'#2D6B3C',PANEL:'#0D1C13',PLINE:'#24402C',name:'Sky blue and forest green',what:'Selected things are bright sky blue with dark text. Other buttons and panels are forest green. Like a clear spring day over the trees.'},
 sun:{A:'#F4C542',AT:'#2A1E02',B:'#2F6E3B',PANEL:'#0D1C13',PLINE:'#24402C',name:'Sunshine and leaf green',what:'Selected things are warm sunshine yellow. Other buttons and panels are leaf green. The sunniest one, but it’s close to the Kids Sunshine colour.'}};
let COL='leaf';const C=()=>PAL[COL];const MUTE='#A9B4BE';
let TOUCH=true;
const playIcon=(x,y,s,c)=>`<path d="M${x} ${y}l${s*.8} ${s/2}l${-s*.8} ${s/2}Z" fill="${c}"/>`;
/* a little Earth: ocean ball with green land, lit from the top left */
function globe(cx,cy,r,turn=0){const p=u('gl');const land=`M-.55 -.7C-.3 -.85 .05 -.8 .1 -.62C.12 -.5 -.02 -.42 -.08 -.3C-.12 -.2 -.2 -.14 -.28 -.12C-.36 -.1 -.42 -.2 -.5 -.26C-.62 -.34 -.72 -.46 -.55 -.7ZM-.2 -.06C-.1 -.04 .04 0 .1 .08C.18 .18 .14 .3 .06 .42C0 .54 -.06 .7 -.14 .78C-.18 .62 -.2 .48 -.24 .34C-.28 .2 -.3 .06 -.2 -.06ZM.62 -.3C.78 -.32 .9 -.2 .92 -.02C.9 .2 .8 .36 .74 .5C.7 .36 .64 .2 .6 .06C.56 -.08 .52 -.22 .62 -.3Z`;
 return `<defs><clipPath id="${p}c"><circle cx="${cx}" cy="${cy}" r="${r}"/></clipPath><radialGradient id="${p}o" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#7CC8FF"/><stop offset=".6" stop-color="#2F7FD0"/><stop offset="1" stop-color="#12407A"/></radialGradient><radialGradient id="${p}d" cx=".35" cy=".3" r=".85"><stop offset=".5" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".45"/></radialGradient></defs>
 <circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#${p}o)"/><g clip-path="url(#${p}c)"><path d="${land}" transform="translate(${f1(cx+turn*r)} ${cy}) scale(${r})" fill="#5DBB4E" stroke="#2E7A2C" stroke-width="${f1(1.2/r)}"/><path d="${land}" transform="translate(${f1(cx+turn*r-2*r)} ${cy}) scale(${r})" fill="#5DBB4E"/><path d="M${cx-r} ${f1(cy-r*.82)}H${cx+r}" stroke="#fff" stroke-width="${f1(r*.18)}" opacity=".8"/></g>
 <circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#${p}d)"/><ellipse cx="${f1(cx-r*.38)}" cy="${f1(cy-r*.42)}" rx="${f1(r*.26)}" ry="${f1(r*.14)}" transform="rotate(-35 ${f1(cx-r*.38)} ${f1(cy-r*.42)})" fill="#fff" opacity=".55"/>`}
/* a two-leaf sprout that grows up from an edge */
function sprout(x,y,s){return `<path d="M${x} ${y}C${x-1} ${y-s*.4} ${x+2} ${y-s*.7} ${x} ${y-s}" stroke="#3E8C34" stroke-width="${f1(s*.09)}" fill="none" stroke-linecap="round"/>`+leaf(x,y-s,-155,s*.85,s*.55,GR[0],GR[1],{kind:'heart'})+leaf(x,y-s,-25,s*.95,s*.6,GR[0],GR[1],{kind:'heart'})}
function bloom(x,y,R){let s='';for(let k=0;k<6;k++){const a=k/6*Math.PI*2;s+=`<ellipse cx="${f1(x+Math.cos(a)*R*.55)}" cy="${f1(y+Math.sin(a)*R*.55)}" rx="${f1(R*.5)}" ry="${f1(R*.34)}" transform="rotate(${f1(a*180/Math.PI)} ${f1(x+Math.cos(a)*R*.55)} ${f1(y+Math.sin(a)*R*.55)})" fill="${k%2?'#FF8FB8':'#FF6F9E'}" stroke="#B23A66" stroke-width=".8"/>`}return s+`<circle cx="${x}" cy="${y}" r="${f1(R*.32)}" fill="#F6C232" stroke="#C98E10"/>`}
function bud(x,y,R){return `<path d="M${x} ${y+R*1.4}V${y+R*.4}" stroke="#3E8C34" stroke-width="2.6"/><ellipse cx="${x}" cy="${y}" rx="${f1(R*.55)}" ry="${f1(R*.85)}" fill="#FF7BA6" stroke="#B23A66"/><path d="M${x} ${y+R*.6}C${x-R*.8} ${y+R*.4} ${x-R*.7} ${y-R*.2} ${x-R*.2} ${y-R*.5}M${x} ${y+R*.6}C${x+R*.8} ${y+R*.4} ${x+R*.7} ${y-R*.2} ${x+R*.2} ${y-R*.5}" stroke="#3E8C34" stroke-width="${f1(R*.25)}" fill="none" stroke-linecap="round"/>`}
const logo=(x,y,s=1)=>`<g transform="translate(${x} ${y}) scale(${s})"><rect width="26" height="26" rx="7" fill="#FFB547"/><rect x="30" width="26" height="26" rx="7" fill="#7AA7FF"/><rect y="30" width="26" height="26" rx="7" fill="#3DD6C6"/><rect x="30" y="30" width="26" height="26" rx="7" fill="#FF6F91"/>${T(74,43,42,'Benflix',{font:'Outfit,sans-serif',w:800})}</g>`;

window.EarthKit = { CHARM, DOORS, DW, DH, GDEF, door, globe, sprout, bloom, bud, leaf, GR };
})();
