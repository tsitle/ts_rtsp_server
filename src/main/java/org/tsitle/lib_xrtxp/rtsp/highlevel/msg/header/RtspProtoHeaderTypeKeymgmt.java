package org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspKeymgmtProto;

public final class RtspProtoHeaderTypeKeymgmt {

	public @NonNull RtspKeymgmtProto proto = RtspKeymgmtProto.NONE;
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
