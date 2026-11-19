import fs from 'node:fs';
import path from 'node:path';

const src = path.resolve('node_modules/@mediapipe/tasks-vision/wasm');
const dest = path.resolve('public/wasm');

if (fs.existsSync(src)) {
  fs.mkdirSync(dest, { recursive: true });
  fs.cpSync(src, dest, { recursive: true, force: true });
  console.log('Copied MediaPipe WASM assets to public/wasm');
}
