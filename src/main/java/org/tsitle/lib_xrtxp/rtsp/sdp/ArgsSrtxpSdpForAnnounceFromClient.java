package org.tsitle.lib_xrtxp.rtsp.sdp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdpRaw;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdpStructured;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoKmdsStream;

public final class ArgsSrtxpSdpForAnnounceFromClient {

	/** Input for the SDP data from the server */
	@NonNull RtspProtoDataCntSdpStructured inputSdpFromServer;
	/** Input for the legacy SDES KMDs (can be null if [doGenerateLegacySdesKmds] is true) */
	@Nullable RtspProtoKmdsStream inputLegacySdesKmdsOutbound;
	/** Whether to generate legacy SDES KMDs (if true [inputLegacySdesKmdsOutbound] will be ignored) */
	boolean doGenerateLegacySdesKmds;
	/** Output for the generated legacy SDES KMDs (can be null if [doGenerateLegacySdesKmds] is false) */
	@Nullable RtspProtoKmdsStream outputLegacySdesKmdsOutbound;
	/** Output for the SDP data from the client */
	@NonNull RtspProtoDataCntSdpRaw outputSdpFromClient;

	ArgsSrtxpSdpForAnnounceFromClient(
				@NonNull RtspProtoDataCntSdpStructured inputSdpFromServer,
				@Nullable RtspProtoKmdsStream inputLegacySdesKmdsOutbound,
				boolean doGenerateLegacySdesKmds,
				@Nullable RtspProtoKmdsStream outputLegacySdesKmdsOutbound,
				@NonNull RtspProtoDataCntSdpRaw outputSdpFromClient
			) {
		this.inputSdpFromServer = inputSdpFromServer;
		this.inputLegacySdesKmdsOutbound = inputLegacySdesKmdsOutbound;
		this.doGenerateLegacySdesKmds = doGenerateLegacySdesKmds;
		this.outputLegacySdesKmdsOutbound = outputLegacySdesKmdsOutbound;
		this.outputSdpFromClient = outputSdpFromClient;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static final class Builder {

		private @Nullable RtspProtoDataCntSdpStructured inputSdpFromServer;
		private @Nullable RtspProtoKmdsStream inputLegacySdesKmdsOutbound = null;
		private @Nullable Boolean doGenerateLegacySdesKmds = null;
		private @Nullable RtspProtoKmdsStream outputLegacySdesKmdsOutbound = null;
		private @Nullable RtspProtoDataCntSdpRaw outputSdpFromClient;

		private Builder() { }

		public static Builder builder() {
			return new Builder();
		}

		/** Input for the SDP data from the server */
		public Builder inputSdpFromServer(@NonNull RtspProtoDataCntSdpStructured v) {
			this.inputSdpFromServer = v;
			return this;
		}

		/** Input for the legacy SDES KMDs (can be null if [doGenerateLegacySdesKmds] is true) */
		public Builder inputLegacySdesKmdsOutbound(@Nullable RtspProtoKmdsStream v) {
			this.inputLegacySdesKmdsOutbound = v;
			return this;
		}

		/** Whether to generate legacy SDES KMDs (if true [inputLegacySdesKmdsOutbound] will be ignored) */
		public Builder doGenerateLegacySdesKmds(boolean v) {
			this.doGenerateLegacySdesKmds = v;
			return this;
		}

		/** Output for the generated legacy SDES KMDs (can be null if [doGenerateLegacySdesKmds] is false) */
		public Builder outputLegacySdesKmdsOutbound(@Nullable RtspProtoKmdsStream v) {
			this.outputLegacySdesKmdsOutbound = v;
			return this;
		}

		/** Output for the SDP data from the client */
		public Builder outputSdpFromClient(@NonNull RtspProtoDataCntSdpRaw v) {
			this.outputSdpFromClient = v;
			return this;
		}

		public @NonNull ArgsSrtxpSdpForAnnounceFromClient build() {
			if (inputSdpFromServer == null) {
				throw new IllegalStateException("inputSdpFromServer must be set");
			}
			if (doGenerateLegacySdesKmds == null) {
				throw new IllegalStateException("doGenerateLegacySdesKmds must be set");
			}
			if (! doGenerateLegacySdesKmds && inputLegacySdesKmdsOutbound == null) {
				throw new IllegalStateException("inputLegacySdesKmdsOutbound must be set");
			}
			if (doGenerateLegacySdesKmds && outputLegacySdesKmdsOutbound == null) {
				throw new IllegalStateException("outputLegacySdesKmdsOutbound must be set");
			}
			if (outputSdpFromClient == null) {
				throw new IllegalStateException("outputSdpFromClient must be set");
			}

			return new ArgsSrtxpSdpForAnnounceFromClient(
					inputSdpFromServer,
					inputLegacySdesKmdsOutbound,
					doGenerateLegacySdesKmds,
					outputLegacySdesKmdsOutbound,
					outputSdpFromClient
				);
		}

	}

}
