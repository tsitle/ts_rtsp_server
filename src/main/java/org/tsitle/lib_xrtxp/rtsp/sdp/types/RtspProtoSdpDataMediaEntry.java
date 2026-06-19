package org.tsitle.lib_xrtxp.rtsp.sdp.types;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public record RtspProtoSdpDataMediaEntry(
			@NonNull RtspProtoSdpDataMediaEntryHeader header,
			@NonNull String title,
			@NonNull RtspProtoSdpDataConnectionMedia connectionInfo,
			@NonNull String bandwidth,
			@NonNull List<@NonNull String> attributes
		) {

	public RtspProtoSdpDataMediaEntry(@NonNull RtspProtoSdpDataMediaEntryHeader header) {
		this(
				new RtspProtoSdpDataMediaEntryHeader(header),
				"",
				new RtspProtoSdpDataConnectionMedia("", "", ""),
				"",
				new ArrayList<>()
			);
		if (header.isEmpty()) {
			throw new IllegalArgumentException("header must not be empty");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"header=" + header +
				(! title.isBlank() ? ", title='" + title + "'" : "") +
				(! connectionInfo.isEmpty() ? ", connectionInfo=" + connectionInfo : "") +
				(! bandwidth.isBlank() ? ", bandwidth='" + bandwidth + "'" : "") +
				(! attributes.isEmpty() ? ", attributes=" + listOfStringsToString(attributes) : "") +
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
