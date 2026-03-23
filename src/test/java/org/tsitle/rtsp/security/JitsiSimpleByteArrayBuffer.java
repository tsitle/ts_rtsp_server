package org.tsitle.rtsp.security;

import java.util.Arrays;

final class JitsiSimpleByteArrayBuffer implements org.jitsi.utils.ByteArrayBuffer {

	private byte[] buffer;
	private int offset;
	private int length;

	public JitsiSimpleByteArrayBuffer(byte[] data) {
		this.buffer = data;
		this.offset = 0;
		this.length = data.length;
	}

	@SuppressWarnings("unused")
	public JitsiSimpleByteArrayBuffer(int capacity) {
		this.buffer = new byte[capacity];
		this.offset = 0;
		this.length = 0;
	}

	@Override public byte[] getBuffer() { return buffer; }
	@Override public int getOffset() { return offset; }
	@Override public void setOffset(int off) { this.offset = off; }
	@Override public int getLength() { return length; }
	@Override public void setLength(int len) { this.length = len; }

	@Override
	public boolean isInvalid() {
		return buffer == null || offset < 0 || length < 0 || offset + length > buffer.length;
	}

	@Override
	public void readRegionToBuff(int off, int len, byte[] outBuff) {
		System.arraycopy(buffer, offset + off, outBuff, 0, len);
	}

	@Override
	public void append(byte[] data, int len) {
		grow(len);
		System.arraycopy(data, 0, buffer, offset + length, len);
		length += len;
	}

	@Override
	public void grow(int howMuch) {
		int needed = offset + length + howMuch;
		if (needed > buffer.length) {
			buffer = Arrays.copyOf(buffer, Math.max(needed, buffer.length * 2));
		}
	}

	@Override
	public void shrink(int len) {
		if (len < 0 || len > length) throw new IllegalArgumentException("Invalid shrink len");
		length -= len;
	}

}
