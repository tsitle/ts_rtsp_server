package org.tsitle.lib_xrtxp.rtsp.sdp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdpRaw;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoAdSettingsStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoKmdsStream;

public final class ArgsSdpForDescribeFromServer {

	/** Prefix for Sub-Stream IDs */
	final @NonNull String cfgSubStreamIdPrefix;
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
	/** Output for the SDP lines */
	final @NonNull RtspProtoDataCntSdpRaw outputSdp;
	/** Output for the stream settings */
	final @NonNull RtspProtoAdSettingsStream outputAdStreamSett;
	/** Output for the KMDs */
	final @NonNull RtspProtoKmdsStream outputKmdsOutbound;

	ArgsSdpForDescribeFromServer(
				@NonNull String cfgSubStreamIdPrefix,
				boolean requireSrtp,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIpAddr serverIpOrName,
				@NonNull String clientUserAgent,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoDataCntSdpRaw outputSdp,
				@NonNull RtspProtoAdSettingsStream outputAdStreamSett,
				@NonNull RtspProtoKmdsStream outputKmdsOutbound
			) {
		this.cfgSubStreamIdPrefix = cfgSubStreamIdPrefix;
		this.requireSrtp = requireSrtp;
		this.idInputSource = idInputSource;
		this.serverIpOrName = serverIpOrName;
		this.clientUserAgent = clientUserAgent;
		this.clientIpAddr = clientIpAddr;
		this.outputSdp = outputSdp;
		this.outputAdStreamSett = outputAdStreamSett;
		this.outputKmdsOutbound = outputKmdsOutbound;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static final class Builder {

		private @Nullable String cfgSubStreamIdPrefix = null;
		private @Nullable Boolean requireSrtp = null;
		private @Nullable RtspProtoIdInputSource idInputSource = null;
		private @Nullable RtspProtoIpAddr serverIpOrName = null;
		private @Nullable String clientUserAgent = null;
		private @Nullable RtspProtoIpAddr clientIpAddr = null;
		private @Nullable RtspProtoDataCntSdpRaw outputSdp = null;
		private @Nullable RtspProtoAdSettingsStream outputAdStreamSett = null;
		private @Nullable RtspProtoKmdsStream outputKmdsOutbound = null;

		private Builder() { }

		public static Builder builder() {
			return new Builder();
		}

		/** Prefix for Sub-Stream IDs */
		public Builder cfgSubStreamIdPrefix(@NonNull String v) {
			this.cfgSubStreamIdPrefix = v;
			return this;
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

		/** Output for the SDP lines */
		public Builder outputSdp(@NonNull RtspProtoDataCntSdpRaw v) {
			this.outputSdp = v;
			return this;
		}

		/** Output for the stream settings */
		public Builder outputAdStreamSett(@NonNull RtspProtoAdSettingsStream v) {
			this.outputAdStreamSett = v;
			return this;
		}

		/** Output for the KMDs */
		public Builder outputKmdsOutbound(@NonNull RtspProtoKmdsStream v) {
			this.outputKmdsOutbound = v;
			return this;
		}

		public @NonNull ArgsSdpForDescribeFromServer build() {
			if (cfgSubStreamIdPrefix == null) {
				throw new IllegalStateException("cfgSubStreamIdPrefix must be set");
			}
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
			if (outputSdp == null) {
				throw new IllegalStateException("outputSdp must be set");
			}
			if (outputAdStreamSett == null) {
				throw new IllegalStateException("outputAdStreamSett must be set");
			}
			if (outputKmdsOutbound == null) {
				throw new IllegalStateException("outputKmdsOutbound must be set");
			}

			return new ArgsSdpForDescribeFromServer(
					cfgSubStreamIdPrefix,
					requireSrtp,
					idInputSource,
					serverIpOrName,
					clientUserAgent,
					clientIpAddr,
					outputSdp,
					outputAdStreamSett,
					outputKmdsOutbound
				);
		}

	}
}
