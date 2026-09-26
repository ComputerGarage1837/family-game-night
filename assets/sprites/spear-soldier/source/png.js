const zlib = require('zlib'), fs = require('fs');
function crc32(buf){let c,crc=0xffffffff;for(let n=0;n<buf.length;n++){c=(crc^buf[n])&0xff;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;crc=(crc>>>8)^c;}return (crc^0xffffffff)>>>0;}
function chunk(type,data){const len=Buffer.alloc(4);len.writeUInt32BE(data.length);const td=Buffer.concat([Buffer.from(type),data]);const crc=Buffer.alloc(4);crc.writeUInt32BE(crc32(td));return Buffer.concat([len,td,crc]);}
function writePNG(file,W,H,rgba){const raw=Buffer.alloc((W*4+1)*H);for(let y=0;y<H;y++){raw[y*(W*4+1)]=0;Buffer.from(rgba.buffer,rgba.byteOffset+y*W*4,W*4).copy(raw,y*(W*4+1)+1);}
const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(W,0);ihdr.writeUInt32BE(H,4);ihdr[8]=8;ihdr[9]=6;ihdr[10]=0;ihdr[11]=0;ihdr[12]=0;
fs.writeFileSync(file,Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ihdr),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]));}
// Compose tiles (each {rgba,W,H}) side by side scaled by s over bg colour.
function sheet(file,tiles,s,bg=[40,44,60]){const W=tiles.reduce((a,t)=>a+t.W*s+8,8),H=Math.max(...tiles.map(t=>t.H*s))+16;const out=new Uint8ClampedArray(W*H*4);for(let i=0;i<W*H;i++){out[i*4]=bg[0];out[i*4+1]=bg[1];out[i*4+2]=bg[2];out[i*4+3]=255;}
let ox=8;for(const t of tiles){for(let y=0;y<t.H*s;y++)for(let x=0;x<t.W*s;x++){const sk=((y/s|0)*t.W+(x/s|0))*4;if(!t.rgba[sk+3])continue;const dk=((y+8)*W+ox+x)*4;out[dk]=t.rgba[sk];out[dk+1]=t.rgba[sk+1];out[dk+2]=t.rgba[sk+2];}ox+=t.W*s+8;}
writePNG(file,W,H,out);}
module.exports={writePNG,sheet};
