/* Glowfin web: the Storybook sea friends (Ben's pick S3, ui-5), the same 8 animals and 8 colours as the TV and
   phone app. B.seaPic(animal, colour) gives the picture as an SVG; colour -1 is the Kids rainbow.
   Everything sits in one closure: the web scripts share one scope. */
(() => {

const COL=[['#a55be0','#1fa8e6'],['#1fa8e6','#3fe0e8'],['#3fe0e8','#2bb88f'],['#e05bb5','#a55be0'],['#f0a04b','#e0605b'],['#5fd08a','#1fa8e6'],['#6c6fe8','#a55be0'],['#5a6488','#2d3354']];
const KIDS=8, RAIN=['#f0a04b','#e05bb5','#a55be0','#3fe0e8'];
const INK='#14172e';
function hx(h){h=h.replace('#','');return [0,2,4].map(i=>parseInt(h.substr(i,2),16));}
function mix(a,b,t){const A=hx(a),B=hx(b);return '#'+A.map((v,i)=>Math.round(v+(B[i]-v)*t).toString(16).padStart(2,'0')).join('');}
let gid=0;
function gradStops(ci){return ci===KIDS?RAIN.map((c,i)=>`<stop offset="${i/3}" stop-color="${c}"/>`).join(''):`<stop offset="0" stop-color="${COL[ci][0]}"/><stop offset="1" stop-color="${COL[ci][1]}"/>`;}
const main=ci=>ci===KIDS?'#a55be0':COL[ci][0];

/* ---------- the 8 animals. p = {b body, s shade, l belly/light, e eye, w eye shine, k blush} ---------- */
function eye(x,y,p,r=5.2){return `<ellipse cx="${x}" cy="${y}" rx="${r*.86}" ry="${r}" fill="${p.e}"/><circle cx="${x+r*.32}" cy="${y-r*.38}" r="${r*.36}" fill="${p.w}"/><circle cx="${x-r*.3}" cy="${y+r*.4}" r="${r*.14}" fill="${p.w}" opacity=".8"/>`;}
function blush(x,y,p){return `<ellipse cx="${x}" cy="${y}" rx="4.4" ry="2.6" fill="${p.k}" opacity=".55"/>`;}
function smile(d,p,w=2.4){return `<path d="${d}" fill="none" stroke="${p.e}" stroke-width="${w}" stroke-linecap="round"/>`;}
const A={
 fish:p=>`<path d="M74 50 L88 37 Q85 50 88 63 Z" fill="${p.s}" stroke="${p.s}" stroke-width="3" stroke-linejoin="round"/>
  <path d="M42 33 Q52 17 64 33 Z" fill="${p.s}" stroke="${p.s}" stroke-width="3" stroke-linejoin="round"/>
  <path d="M48 67 Q54 78 62 67 Z" fill="${p.s}" stroke="${p.s}" stroke-width="2" stroke-linejoin="round"/>
  <path d="M20 51 C20 36 36 29 52 30 C66 31 75 40 76 50 C75 60 66 69 52 70 C36 71 20 66 20 51Z" fill="${p.b}"/>
  <path d="M24 58 C32 67 48 70 62 66 C50 66 34 64 24 58Z" fill="${p.l}"/>
  <path d="M58 32 Q52 50 58 68" fill="none" stroke="${p.s}" stroke-width="3.4" stroke-linecap="round" opacity=".7"/>
  <path d="M66 36 Q61 50 66 64" fill="none" stroke="${p.s}" stroke-width="3" stroke-linecap="round" opacity=".5"/>
  ${eye(36,46,p,6)}${blush(44,56,p)}${smile('M28 56 q4 3.6 8 0',p)}`,
 whale:p=>`<path d="M74 58 L86 46 Q92 46 89 53 L86 59 Q92 66 86 68 L76 63 Z" fill="${p.s}" stroke="${p.s}" stroke-width="2" stroke-linejoin="round"/>
  <path d="M14 58 C14 41 30 33 48 34 C66 35 78 46 78 58 C78 70 64 77 46 77 C26 77 14 70 14 58Z" fill="${p.b}"/>
  <path d="M18 64 C26 74 48 78 70 68 C64 71 40 72 18 64Z" fill="${p.l}"/>
  <path d="M22 66 h2 M30 70 h2 M38 72 h2" stroke="${p.s}" stroke-width="2" stroke-linecap="round" opacity=".6"/>
  <path d="M48 66 Q56 74 62 62" fill="${p.s}" opacity=".8"/>
  <path d="M38 34 Q38 24 38 20 M38 22 Q32 14 26 16 M38 22 Q44 14 50 16" fill="none" stroke="${p.b}" stroke-width="3.6" stroke-linecap="round"/>
  ${eye(30,52,p,5.4)}${blush(37,60,p)}${smile('M22 60 q4 3.4 8 0',p)}`,
 octopus:p=>`${[['M32 60 Q22 66 20 76 Q19 82 25 81',0],['M40 62 Q36 74 38 82 Q40 87 44 84',0],['M60 62 Q64 74 62 82 Q60 87 56 84',0],['M68 60 Q78 66 80 76 Q81 82 75 81',0]].map(([d])=>`<path d="${d}" fill="none" stroke="${p.b}" stroke-width="8" stroke-linecap="round"/>`).join('')}
  <path d="M50 62 Q50 76 50 84" fill="none" stroke="${p.s}" stroke-width="7.5" stroke-linecap="round"/>
  <path d="M26 50 C26 28 40 18 50 18 C60 18 74 28 74 50 C74 60 64 66 50 66 C36 66 26 60 26 50Z" fill="${p.b}"/>
  <path d="M34 28 Q42 21 50 21" fill="none" stroke="${p.l}" stroke-width="3.4" stroke-linecap="round"/>
  <circle cx="64" cy="32" r="3" fill="${p.s}" opacity=".7"/><circle cx="69" cy="40" r="2" fill="${p.s}" opacity=".7"/><circle cx="58" cy="26" r="1.8" fill="${p.s}" opacity=".7"/>
  ${eye(41,46,p)}${eye(59,46,p)}${blush(35,55,p)}${blush(65,55,p)}${smile('M46 55 q4 3.6 8 0',p)}`,
 jelly:p=>`${['M34 58 Q30 66 35 72 Q40 78 35 86','M45 60 Q41 68 46 74 Q51 80 46 88','M55 60 Q51 68 56 74 Q61 80 56 88','M66 58 Q62 66 67 72 Q72 78 67 86'].map(d=>`<path d="${d}" fill="none" stroke="${p.s}" stroke-width="3.6" stroke-linecap="round"/>`).join('')}
  <path d="M24 54 C24 30 38 20 50 20 C62 20 76 30 76 54 Q71 59 65.5 55 Q60 60 55 56 Q50 60 45 56 Q40 60 34.5 55 Q29 59 24 54Z" fill="${p.b}"/>
  <path d="M32 34 Q38 26 46 24" fill="none" stroke="${p.l}" stroke-width="3.6" stroke-linecap="round"/>
  <path d="M28 50 Q50 46 72 50" fill="none" stroke="${p.s}" stroke-width="2.2" opacity=".5"/>
  ${eye(42,40,p)}${eye(58,40,p)}${blush(36,47,p)}${blush(64,47,p)}${smile('M46 46 q4 3.4 8 0',p)}`,
 turtle:p=>`<ellipse cx="30" cy="68" rx="7" ry="5" fill="${p.s}"/><ellipse cx="62" cy="68" rx="7" ry="5" fill="${p.s}"/><path d="M18 62 L10 60 L16 66Z" fill="${p.s}"/>
  <circle cx="76" cy="50" r="11" fill="${p.b}"/>
  <path d="M16 62 C16 40 30 30 46 30 C62 30 76 40 76 62Z" fill="${p.s}"/>
  <path d="M46 36 l7 4 v8 l-7 4 l-7-4 v-8z M31 46 l6 3.5 v7 M61 46 l-6 3.5 v7" fill="none" stroke="${p.l}" stroke-width="2.4" stroke-linejoin="round" opacity=".85"/>
  <path d="M22 44 Q30 34 42 33" fill="none" stroke="${p.l}" stroke-width="3" stroke-linecap="round" opacity=".7"/>
  <rect x="13" y="60" width="66" height="7" rx="3.5" fill="${p.b}"/>
  ${eye(79,47,p,4.6)}${blush(80,55,p)}${smile('M80 54 q3 2.4 6 -.6',p,2)}`,
 crab:p=>`${['M28 62 L16 66','M28 67 L18 74','M72 62 L84 66','M72 67 L82 74'].map(d=>`<path d="${d}" stroke="${p.s}" stroke-width="4.4" stroke-linecap="round"/>`).join('')}
  <path d="M30 54 L22 44 M70 54 L78 44" stroke="${p.b}" stroke-width="5.4" stroke-linecap="round"/>
  <path d="M10 36 C10 26 22 22 28 30 L22 34 L30 38 C28 46 12 46 10 36Z" fill="${p.b}"/><path d="M90 36 C90 26 78 22 72 30 L78 34 L70 38 C72 46 88 46 90 36Z" fill="${p.b}"/>
  <path d="M42 50 L40 36 M58 50 L60 36" stroke="${p.b}" stroke-width="4" stroke-linecap="round"/>
  <path d="M22 60 C22 46 36 42 50 42 C64 42 78 46 78 60 C78 70 66 75 50 75 C34 75 22 70 22 60Z" fill="${p.b}"/>
  <path d="M28 66 C36 73 64 73 72 66 C64 70 36 70 28 66Z" fill="${p.l}"/>
  <circle cx="40" cy="33" r="6.4" fill="${p.w}"/><circle cx="60" cy="33" r="6.4" fill="${p.w}"/>${eye(40.6,33.6,p,3.8)}${eye(60.6,33.6,p,3.8)}
  ${blush(34,60,p)}${blush(66,60,p)}${smile('M44 58 q6 5 12 0',p)}`,
 puffer:p=>`${Array.from({length:12},(_,i)=>{const a=i/12*Math.PI*2,x=50+Math.cos(a)*25,y=52+Math.sin(a)*25,x2=50+Math.cos(a)*32,y2=52+Math.sin(a)*32,dx=Math.cos(a+Math.PI/2)*3.4,dy=Math.sin(a+Math.PI/2)*3.4;
   return `<path d="M${(x+dx).toFixed(1)} ${(y+dy).toFixed(1)} L${x2.toFixed(1)} ${y2.toFixed(1)} L${(x-dx).toFixed(1)} ${(y-dy).toFixed(1)}Z" fill="${p.s}" stroke="${p.s}" stroke-width="1.6" stroke-linejoin="round"/>`;}).join('')}
  <circle cx="50" cy="52" r="27" fill="${p.b}"/>
  <path d="M28 62 C34 76 66 76 72 62 C64 70 36 70 28 62Z" fill="${p.l}"/>
  <path d="M34 34 Q42 27 52 27" fill="none" stroke="${p.l}" stroke-width="3.4" stroke-linecap="round"/>
  ${eye(41,48,p,5.6)}${eye(59,48,p,5.6)}${blush(34,58,p)}${blush(66,58,p)}<ellipse cx="50" cy="61" rx="3.6" ry="4.2" fill="${p.e}"/>`,
 star:p=>{let d='';for(let i=0;i<10;i++){const a=-Math.PI/2+i*Math.PI/5,r=i%2?17:34;d+=(i?'L':'M')+(50+Math.cos(a)*r).toFixed(1)+' '+(54+Math.sin(a)*r).toFixed(1);}d+='Z';
  return `<path d="${d}" fill="${p.b}" stroke="${p.b}" stroke-width="9" stroke-linejoin="round"/>
  ${[[50,32],[30,48],[70,48],[38,72],[62,72]].map(([x,y])=>`<circle cx="${x}" cy="${y}" r="2.4" fill="${p.s}" opacity=".75"/>`).join('')}
  <path d="M40 30 Q46 22 50 20" fill="none" stroke="${p.l}" stroke-width="3" stroke-linecap="round"/>
  ${eye(43,50,p,4.8)}${eye(57,50,p,4.8)}${blush(38,58,p)}${blush(62,58,p)}${smile('M46 58 q4 3.4 8 0',p)}`;},
};
const KEYS=['fish','whale','octopus','jelly','turtle','crab','puffer','star'];
const LABEL={fish:'Fish',whale:'Whale',octopus:'Octopus',jelly:'Jellyfish',turtle:'Turtle',crab:'Crab',puffer:'Puffer',star:'Starfish'};

function s3(k,ci){/* Storybook: full-colour animal in soft tints on a deep version of your colour, bubbles */
 const id='c'+(++gid),m=main(ci);const p={b:mix(m,'#ffffff',.5),s:mix(m,'#ffffff',.2),l:mix(m,'#ffffff',.82),e:INK,w:'#fff',k:'#ff7aa2'};
 const deep=ci===KIDS?RAIN.map(c=>mix(c,'#0b0d1a',.45)):[mix(COL[ci][0],'#0b0d1a',.5),mix(COL[ci][1],'#0b0d1a',.35)];
 return `<defs><linearGradient id="${id}" x1="0" y1="1" x2="1" y2="0">${deep.map((c,i)=>`<stop offset="${i/(deep.length-1)}" stop-color="${c}"/>`).join('')}</linearGradient>
 <clipPath id="${id}c"><circle cx="50" cy="50" r="50"/></clipPath></defs>
 <g clip-path="url(#${id}c)"><circle cx="50" cy="50" r="50" fill="url(#${id})"/>
 <path d="M0 80 Q25 72 50 80 T100 80 V100 H0Z" fill="#fff" opacity=".08"/>
 <circle cx="82" cy="24" r="3.4" fill="none" stroke="#fff" stroke-opacity=".5" stroke-width="1.4"/><circle cx="88" cy="34" r="2" fill="none" stroke="#fff" stroke-opacity=".45" stroke-width="1.2"/><circle cx="16" cy="30" r="2.4" fill="none" stroke="#fff" stroke-opacity=".35" stroke-width="1.2"/>
 <g transform="translate(9 9) scale(.82)">${A[k](p)}</g></g>
 <circle cx="50" cy="50" r="49" fill="none" stroke="#fff" stroke-opacity=".18" stroke-width="2"/>`;}

  B.SEA_KEYS = KEYS;
  B.SEA_LABEL = LABEL;
  B.SEA_COLOURS = COL;
  B.seaPic = (animal, colour) => {
    const k = KEYS[((animal % 8) + 8) % 8];
    const ci = colour === -1 ? KIDS : ((colour % 8) + 8) % 8;
    return `<svg class="sea" viewBox="0 0 100 100" aria-hidden="true">${s3(k, ci)}</svg>`;
  };
})();
