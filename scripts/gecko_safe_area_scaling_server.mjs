#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';

const fixtureUrl = new URL(
  '../app/src/androidTest/assets/gecko_safe_area_scaling_fixture.html',
  import.meta.url,
);
const maximumReportBytes = 64 * 1024;
const maximumReports = 100;

export function createScalingFixtureServer(html) {
  const reports = [];
  return createServer(async (request, response) => {
    response.setHeader('Cache-Control', 'no-store');
    const url = new URL(request.url, 'http://127.0.0.1');
    if (request.method === 'GET' && url.pathname === '/safe-area-scaling') {
      response.setHeader('Content-Type', 'text/html; charset=utf-8');
      response.end(html);
      return;
    }
    if (request.method === 'GET' && url.pathname === '/reports') {
      response.setHeader('Content-Type', 'application/json');
      response.end(JSON.stringify(reports));
      return;
    }
    const queryReport = request.method === 'GET' && url.pathname === '/safe-area-scaling/report';
    const bodyReport = request.method === 'POST' && url.pathname === '/safe-area-report';
    if (!queryReport && !bodyReport) {
      response.writeHead(404).end();
      return;
    }
    let bytes = 0;
    const chunks = [];
    try {
      if (queryReport) {
        const data = url.searchParams.get('data') || '';
        if (Buffer.byteLength(data) > maximumReportBytes) {
          response.writeHead(413).end();
          return;
        }
        chunks.push(Buffer.from(data));
      } else {
        for await (const chunk of request) {
          bytes += chunk.length;
          if (bytes > maximumReportBytes) {
            response.writeHead(413).end();
            return;
          }
          chunks.push(chunk);
        }
      }
      const report = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      if (!report || typeof report !== 'object' || Array.isArray(report)) {
        response.writeHead(400).end();
        return;
      }
      reports.push(report);
      if (reports.length > maximumReports) reports.shift();
      response.writeHead(204).end();
    } catch (_error) {
      response.writeHead(400).end();
    }
  });
}

function main() {
  const port = Number(process.argv[2] || 0);
  if (!Number.isInteger(port) || port < 0 || port > 65535) {
    throw new Error('Port must be an integer between 0 and 65535');
  }
  const server = createScalingFixtureServer(readFileSync(fixtureUrl));
  server.listen(port, '127.0.0.1', () => {
    process.stdout.write(`http://127.0.0.1:${server.address().port}/safe-area-scaling\n`);
  });
  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.once(signal, () => server.close());
  }
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) main();
