import express from 'express';
import { createServer as createViteServer } from 'vite';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { execFile } from 'child_process';
import { promisify } from 'util';

const execFileAsync = promisify(execFile);

async function startServer() {
  const app = express();
  const PORT = 3000;
  const rootDir = process.cwd();

  // List bundled offline tracks inside public/assets/music (and mirrored in android-project/app/src/main/assets/music)
  app.get('/api/music-catalog', (_req, res) => {
    const musicDir = path.join(rootDir, 'public', 'assets', 'music');
    if (!fs.existsSync(musicDir)) {
      res.json({ tracks: [] });
      return;
    }

    const files = fs
      .readdirSync(musicDir)
      .filter((name) => /\.(mp3|m4a|wav|ogg)$/i.test(name))
      .sort();

    const tracks = files.map((fileName) => {
      const stat = fs.statSync(path.join(musicDir, fileName));
      const title = fileName
        .replace(/\.[^.]+$/, '')
        .split('_')
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ');
      return {
        id: `asset_${fileName}`,
        fileName,
        title,
        url: `/assets/music/${encodeURIComponent(fileName)}`,
        sizeBytes: stat.size,
      };
    });

    res.json({ tracks });
  });

  // Package the full native Android Studio Kotlin/Compose/Media3 codebase as a downloadable ZIP
  app.get('/api/android-project.zip', async (_req, res) => {
    const androidDir = path.join(rootDir, 'android-project');
    if (!fs.existsSync(androidDir)) {
      res.status(404).json({ error: 'Android project directory not found' });
      return;
    }

    const tmpZip = path.join(os.tmpdir(), `trisvax-studio-android-${Date.now()}.zip`);
    try {
      await execFileAsync('python3', [
        '-c',
        `
import os, sys, zipfile
src = sys.argv[1]
dst = sys.argv[2]
with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(src):
        dirs[:] = [d for d in dirs if d not in ('.gradle', 'build', '.idea')]
        for f in files:
            full = os.path.join(root, f)
            rel = os.path.relpath(full, os.path.dirname(src))
            zf.write(full, rel)
`,
        androidDir,
        tmpZip,
      ]);

      res.setHeader('Content-Type', 'application/zip');
      res.setHeader('Content-Disposition', 'attachment; filename="trisvax-studio-android.zip"');
      const stream = fs.createReadStream(tmpZip);
      stream.pipe(res);
      stream.on('close', () => {
        fs.unlink(tmpZip, () => {});
      });
    } catch (err) {
      fs.unlink(tmpZip, () => {});
      res.status(500).json({ error: 'Failed to archive Android project', detail: String(err) });
    }
  });

  // Transcode rendered timeline WebM capture to genuine 1080p H.264/AAC MP4
  app.post(
    '/api/export-mp4',
    express.raw({ type: '*/*', limit: '250mb' }),
    async (req, res) => {
      const rawBuffer = req.body as Buffer;
      if (!Buffer.isBuffer(rawBuffer) || rawBuffer.length === 0) {
        res.status(400).json({ error: 'Empty video payload' });
        return;
      }

      const width = Math.min(1920, Math.max(480, Number(req.query.width) || 1080));
      const height = Math.min(1920, Math.max(480, Number(req.query.height) || 1920));
      const fps = Math.min(60, Math.max(24, Number(req.query.fps) || 30));

      const stamp = Date.now();
      const inputWebm = path.join(os.tmpdir(), `trisvax-in-${stamp}.webm`);
      const outputMp4 = path.join(os.tmpdir(), `trisvax-out-${stamp}.mp4`);

      try {
        fs.writeFileSync(inputWebm, rawBuffer);
        await execFileAsync('ffmpeg', [
          '-y',
          '-i',
          inputWebm,
          '-vf',
          `scale=${width}:${height}:force_original_aspect_ratio=decrease,pad=${width}:${height}:(ow-iw)/2:(oh-ih)/2:color=#0B0807`,
          '-r',
          String(fps),
          '-c:v',
          'libx264',
          '-preset',
          'veryfast',
          '-crf',
          '21',
          '-pix_fmt',
          'yuv420p',
          '-c:a',
          'aac',
          '-b:a',
          '160k',
          '-movflags',
          '+faststart',
          outputMp4,
        ]);

        const mp4Bytes = fs.readFileSync(outputMp4);
        res.setHeader('Content-Type', 'video/mp4');
        res.setHeader('Content-Disposition', 'attachment; filename="TRISVAX_Export_1080p.mp4"');
        res.send(mp4Bytes);
      } catch (err) {
        res.status(500).json({ error: 'FFmpeg 1080p MP4 transcode failed', detail: String(err) });
      } finally {
        fs.unlink(inputWebm, () => {});
        fs.unlink(outputMp4, () => {});
      }
    }
  );

  // Explicit 404 for unknown /api routes
  app.all('/api/*', (_req, res) => {
    res.status(404).json({ error: 'API route not found' });
  });

  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distDir = path.join(rootDir, 'dist');
    app.use(express.static(distDir));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`TRISVAX Studio running on http://localhost:${PORT}`);
  });
}

startServer();
