/**
 * CutsZoom AI — Secure Server-Side Gemini API Proxy
 * Keeps GEMINI_API_KEY strictly on the server.
 * Client APK never receives or stores the API key.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

// Simple .env reader for local development if present
function loadEnvFile() {
  const envPath = path.resolve(__dirname, '.env');
  if (fs.existsSync(envPath)) {
    try {
      const content = fs.readFileSync(envPath, 'utf8');
      content.split('\n').forEach(line => {
        const trimmed = line.trim();
        if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
          const [key, ...values] = trimmed.split('=');
          const val = values.join('=').trim().replace(/^["']|["']$/g, '');
          if (!process.env[key.trim()]) {
            process.env[key.trim()] = val;
          }
        }
      });
    } catch (e) {
      console.warn('Could not read .env file:', e.message);
    }
  }
}

loadEnvFile();

// Port configuration: In this container environment, nginx listens on 8080 and proxies to 3000
const PORT = parseInt(
  process.env.APP_PORT ||
  process.env.BACKEND_PORT ||
  (process.env.PORT === '8080' ? '3000' : (process.env.PORT || '3000')),
  10
);
const GEMINI_MODEL = 'gemini-3.5-flash';
const GEMINI_API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models';

function getApiKey() {
  const key = process.env.GEMINI_API_KEY || '';
  if (!key || key.trim() === '' || key === 'your_gemini_api_key_here' || key === 'MY_GEMINI_API_KEY') {
    return null;
  }
  return key.trim();
}

const server = http.createServer(async (req, res) => {
  // CORS Headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  // Health Check
  if (req.method === 'GET' && (url.pathname === '/health' || url.pathname === '/api/health')) {
    const hasKey = !!getApiKey();
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      service: 'CutsZoom AI Secure Backend',
      geminiConfigured: hasKey,
      model: GEMINI_MODEL
    }));
    return;
  }

  // Speech Semantic Analysis Endpoint
  if (req.method === 'POST' && url.pathname === '/api/analyze-speech') {
    let body = '';
    req.on('data', chunk => {
      body += chunk;
      // Protect against overly large payloads (limit 1MB)
      if (body.length > 1024 * 1024) {
        res.writeHead(413, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Payload too large' }));
        req.destroy();
      }
    });

    req.on('end', async () => {
      try {
        const payload = JSON.parse(body || '{}');
        const apiKey = getApiKey();

        if (!apiKey) {
          console.warn('[CutsZoom Backend] GEMINI_API_KEY is not configured on the server.');
          res.writeHead(503, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({
            error: 'AI analysis is temporarily unavailable. Please configure the GEMINI_API_KEY on the server.',
            configured: false
          }));
          return;
        }

        const fullText = payload.fullText || '';
        const words = payload.words || [];
        const videoDurationSec = payload.speechDurationSeconds || 10.0;
        const videoFps = payload.videoFps || 30.0;

        const wordDump = words.slice(0, 150).map(w =>
          `- "${w.word}" [start=${w.start}s, end=${w.end}s, pauseAfter=${w.pauseAfterMs || 0}ms]`
        ).join('\n');

        const promptText = `
Identify all natural speech, sentence, and semantic thought boundaries for video zoom keyframes.

Video Duration: ${videoDurationSec} seconds
Full Transcript: "${fullText}"

Word-level timings:
${wordDump}

Requirements:
1. Identify natural phrase/sentence boundaries.
2. Each boundary must be formatted as:
   {
     "time": number (seconds),
     "reason": string (why this boundary punctuates the speech),
     "confidence": number (between 0.75 and 0.99)
   }
3. Closely spaced natural boundaries are allowed. Do NOT impose an arbitrary 2-3 second limit.

Return a JSON object:
{
   "summary": "Brief executive analysis of speaker cadence",
   "speechRhythmPace": "Fast / Natural / Deliberate",
   "boundaries": [
     { "time": 2.45, "reason": "Complete introductory thought and vocal cadence drop", "confidence": 0.94 }
   ]
}
`.trim();

        const geminiRequestBody = {
          contents: [
            {
              parts: [{ text: promptText }]
            }
          ],
          systemInstruction: {
            parts: [
              {
                text: "You are an expert film director and AI video rhythm editor for CutsZoom AI. " +
                  "Analyze the speech transcript, word timestamps, pauses, and syntax. " +
                  "Detect natural semantic phrase/sentence boundaries where dynamic camera zoom-out (punch-out) accentuates meaning. " +
                  "Output strictly valid JSON conforming to the requested schema. " +
                  "Do NOT impose an arbitrary minimum spacing. Closely spaced natural boundaries are allowed."
              }
            ]
          },
          generationConfig: {
            responseMimeType: "application/json",
            temperature: 0.2
          }
        };

        const geminiUrl = `${GEMINI_API_BASE}/${GEMINI_MODEL}:generateContent?key=${apiKey}`;
        const geminiResponse = await fetch(geminiUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(geminiRequestBody)
        });

        if (!geminiResponse.ok) {
          const errText = await geminiResponse.text();
          console.error(`[CutsZoom Backend] Gemini API error (${geminiResponse.status}):`, errText);
          res.writeHead(502, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({
            error: 'AI analysis is temporarily unavailable. Please configure the GEMINI_API_KEY on the server.',
            details: `Gemini upstream status: ${geminiResponse.status}`
          }));
          return;
        }

        const geminiJson = await geminiResponse.json();
        const candidateText = geminiJson.candidates?.[0]?.content?.parts?.[0]?.text;

        if (!candidateText) {
          throw new Error('Empty candidate response from Gemini model');
        }

        const parsedContent = JSON.parse(candidateText);
        const cleanBoundaries = (parsedContent.boundaries || []).map(item => ({
          time: Math.round(Number(item.time) * 100) / 100,
          reason: item.reason || 'Semantic thought completion',
          confidence: Math.min(0.99, Math.max(0.70, Number(item.confidence) || 0.90))
        })).sort((a, b) => a.time - b.time);

        const result = {
          boundaries: cleanBoundaries,
          summary: parsedContent.summary || `Detected ${cleanBoundaries.length} natural semantic zoom boundaries.`,
          speechRhythmPace: parsedContent.speechRhythmPace || 'Dynamic Conversational',
          totalKeyframesSuggested: cleanBoundaries.length * 7
        };

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(result));
      } catch (err) {
        console.error('[CutsZoom Backend] Processing error:', err);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          error: 'AI analysis is temporarily unavailable. Please configure the GEMINI_API_KEY on the server.',
          message: err.message
        }));
      }
    });
    return;
  }

  // Default Not Found
  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Endpoint not found' }));
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[CutsZoom AI Backend] Listening on port ${PORT}`);
  console.log(`[CutsZoom AI Backend] Gemini API Key configured: ${getApiKey() ? 'YES' : 'NO (Missing)'}`);
});
