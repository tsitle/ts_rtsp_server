package org.tsitle.lib_xrtxp.rtsp.sdp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdpRaw;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoAdSettingsStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoKmdsStream;

public final class ArgsUpdatedSdpForAnnounceFromServer {

	/** Whether SRTP is required */
	final boolean requireSrtp;
	/** Input Source ID */
	final @NonNull RtspProtoIdInputSource idInputSource;
	/** Server's IP address or hostname */
	final @NonNull RtspProtoIpAddr serverIpOrName;
	/** Client's User-Agent string */
	final @NonNull String clientUserAgent;
	/** Client's IP address */
	final @NonNull RtspProtoIpAddr clientIpAddr;
	/** Input for the stream settings */
	final @NonNull RtspProtoAdSettingsStream inputAdStreamSett;
	/** Optional Key Management Data for outbound RTP/SRTP packets */
	final @Nullable RtspProtoKmdsStream inputKmdsOutbound;
	/** Output for the SDP lines */
	final @NonNull RtspProtoDataCntSdpRaw outputSdp;

	ArgsUpdatedSdpForAnnounceFromServer(
				boolean requireSrtp,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIpAddr serverIpOrName,
				@NonNull String clientUserAgent,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoAdSettingsStream inputAdStreamSett,
				@Nullable RtspProtoKmdsStream inputKmdsOutbound,
				@NonNull RtspProtoDataCntSdpRaw outputSdp
			) {
		this.requireSrtp = requireSrtp;
		this.idInputSource = idInputSource;
		this.serverIpOrName = serverIpOrName;
		this.clientUserAgent = clientUserAgent;
		this.clientIpAddr = clientIpAddr;
		this.inputAdStreamSett = inputAdStreamSett;
		this.inputKmdsOutbound = inputKmdsOutbound;
		this.outputSdp = outputSdp;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static final class Builder {

		private @Nullable Boolean requireSrtp = null;
		private @Nullable RtspProtoIdInputSource idInputSource = null;
		private @Nullable RtspProtoIpAddr serverIpOrName = null;
		private @Nullable String clientUserAgent = null;
		private @Nullable RtspProtoIpAddr clientIpAddr = null;
		private @Nullable RtspProtoAdSettingsStream inputAdStreamSett = null;
		private @Nullable RtspProtoKmdsStream inputKmdsOutbound = null;
		private @Nullable RtspProtoDataCntSdpRaw outputSdp = null;

		private Builder() { }

		public static Builder builder() {
			return new Builder();
		}

		/** Whether SRTP is required */
		public Builder requireSrtp(boolean v) {
			this.requireSrtp = v;
			return this;
		}

		/** Input Source ID */
		public Builder idInputSource(@NonNull RtspProtoIdInputSource v) {
			this.idInputSource = v;
			return this;
		}

		/** Server's IP address or hostname */
		public Builder serverIpOrName(@NonNull RtspProtoIpAddr v) {
			this.serverIpOrName = v;
			return this;
		}

		/** Client's User-Agent string */
		public Builder clientUserAgent(@NonNull String v) {
			this.clientUserAgent = v;
			return this;
		}

		/** Client's IP address */
		public Builder clientIpAddr(@NonNull RtspProtoIpAddr v) {
			this.clientIpAddr = v;
			return this;
		}

		/** Input for the stream settings */
		public Builder inputAdStreamSett(@NonNull RtspProtoAdSettingsStream v) {
			this.inputAdStreamSett = v;
			return this;
		}

		/** Optional Key Management Data for outbound RTP/SRTP packets */
		public Builder inputKmdsOutbound(@Nullable RtspProtoKmdsStream v) {
			this.inputKmdsOutbound = v;
			return this;
		}

		/** Output for the SDP lines */
		public Builder outputSdp(@NonNull RtspProtoDataCntSdpRaw v) {
			this.outputSdp = v;
			return this;
		}

		public @NonNull ArgsUpdatedSdpForAnnounceFromServer build() {
			if (requireSrtp == null) {
				throw new IllegalStateException("requireSrtp must be set");
			}
			if (idInputSource == null) {
				throw new IllegalStateException("idInputSource must be set");
			}
			if (serverIpOrName == null) {
				throw new IllegalStateException("serverIpOrName must be set");
			}
			if (clientUserAgent == null) {
				throw new IllegalStateException("clientUserAgent must be set");
			}
			if (clientIpAddr == null) {
				throw new IllegalStateException("clientIpAddr must be set");
			}
			if (inputAdStreamSett == null) {
				throw new IllegalStateException("inputAdStreamSett must be set");
			}
			if (outputSdp == null) {
				throw new IllegalStateException("outputSdp must be set");
			}

			return new ArgsUpdatedSdpForAnnounceFromServer(
					requireSrtp,
					idInputSource,
					serverIpOrName,
					clientUserAgent,
					clientIpAddr,
					inputAdStreamSett,
					inputKmdsOutbound,
					outputSdp
				);
		}

	}

}
