import { NextRequest, NextResponse } from 'next/server';

export async function GET(
  request: NextRequest,
  { params }: { params: { path: string[] } }
) {
  const backendUrl = request.headers.get('x-schoollog-backend-url');
  const supportRole = request.headers.get('x-schoollog-support-role') || 'SCHOOLLOG_SUPPORT';
  if (!backendUrl) {
    return NextResponse.json({ message: 'Missing backend URL' }, { status: 400 });
  }

  const targetPath = params.path.join('/');
  const target = `${backendUrl.replace(/\/$/, '')}/${targetPath}${request.nextUrl.search}`;
  const response = await fetch(target, {
    headers: {
      'x-schoollog-support-role': supportRole
    },
    cache: 'no-store'
  });
  const text = await response.text();
  return new NextResponse(text, {
    status: response.status,
    headers: {
      'content-type': response.headers.get('content-type') || 'application/json'
    }
  });
}
