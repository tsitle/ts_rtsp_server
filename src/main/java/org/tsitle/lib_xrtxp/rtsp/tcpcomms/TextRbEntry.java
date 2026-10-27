package org.tsitle.lib_xrtxp.rtsp.tcpcomms;

import org.jspecify.annotations.NonNull;

final class TextRbEntry implements Cloneable {

	public @NonNull String text = "";

	@Override
	public TextRbEntry clone() {
		try {
			TextRbEntry clone = (TextRbEntry)super.clone();
			//noinspection StringOperationCanBeSimplified
			clone.text = new String(text);
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException();
		}
	}

}
