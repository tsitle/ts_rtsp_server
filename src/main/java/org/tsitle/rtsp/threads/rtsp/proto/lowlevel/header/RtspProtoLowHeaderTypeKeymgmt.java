package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeKeymgmt {

	public enum KeymgmtProto {
		NONE,
		MIKEY
	}

	public @NonNull KeymgmtProto proto = KeymgmtProto.NONE;
	public @NonNull String dataStr = "";
	public @NonNull String uriStr = "";

	@Override
	public @NonNull String toString() {
		return "[" +
				"proto=" + proto +
				", dataStr='" + dataStr + "'" +
				", uriStr='" + uriStr + "'" +
				"]";
	}

}
