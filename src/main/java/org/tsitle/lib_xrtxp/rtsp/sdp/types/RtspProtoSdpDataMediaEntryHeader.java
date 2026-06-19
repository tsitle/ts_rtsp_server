package org.tsitle.lib_xrtxp.rtsp.sdp.types;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSocketPortNr;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpMediaType;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpTransport;

import java.util.ArrayList;
import java.util.List;

public record RtspProtoSdpDataMediaEntryHeader(
			@NonNull RtspProtoSdpMediaType mediaType,
			@NonNull RtspProtoSocketPortNr portNr,
			int portCount,
			@NonNull RtspProtoSdpTransport transport,
			@NonNull List<@NonNull String> formatList
		) {

	public RtspProtoSdpDataMediaEntryHeader(@NonNull RtspProtoSdpDataMediaEntryHeader other) {
		this(other.mediaType, other.portNr.clone(), other.portCount, other.transport, new ArrayList<>(other.formatList));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		// portNr can be empty (aka 0)
		return (mediaType == RtspProtoSdpMediaType.UNKNOWN || portCount < 1 ||
				transport == RtspProtoSdpTransport.UNKNOWN || formatList.isEmpty());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"mediaType=" + mediaType +
				", portNr=" + (portNr.isEmpty() ? "0" : Integer.toUnsignedString(portNr.getPort16bit().orElseThrow())) +
				", portCount=" + Integer.toUnsignedString(portCount) +
				", transport=" + transport +
				", formatList=" + listOfStringsToString(formatList) +
				"]";
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String listOfStringsToString(@NonNull List<@NonNull String> input) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean isFirst = true;
		for (String tmpEntry : input) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append("'").append(tmpEntry).append("'");
			isFirst = false;
		}
		sb.append("}");
		return sb.toString();
	}

}
