package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.helpers.RandomHelper;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Container for SETUP information for a Stream (with up to two Sub-Streams).
 */
public final class RtspProtoSetupInfosStream implements Cloneable {

	private final @NonNull RtspProtoIdSubStream idSubStream1 = RtspProtoIdSubStream.ofEmpty();
	private @Nullable RtspProtoSetupInfoForSubStream siSsPtr1 = null;  // store pointer since it contains UDP sockets
	private final @NonNull RtspProtoIdSubStream idSubStream2 = RtspProtoIdSubStream.ofEmpty();
	private @Nullable RtspProtoSetupInfoForSubStream siSsPtr2 = null;  // store pointer since it contains UDP sockets

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void createAndAddSetupSubStream(
				@NonNull RtspProtoRscUrl rscUrlSubStream,
				@NonNull RtspProtoIdXsrc ssrcOutbound,
				@NonNull RtspProtoKmdForSubStream kmdOutbound
			) {
		createAndAddSetupDescribeSubStream(
				rscUrlSubStream,
				null,
				ssrcOutbound,
				RtspProtoRtpSeqNr.withOverflow(RandomHelper.getRandomUint16()),
				RtspProtoRtpTimestamp.withOverflow(RandomHelper.getRandomUint32(true)),
				TimestampMonotonic.ofNow(),
				null,
				kmdOutbound
			);
	}

	public void createAndAddDescribeSubStream(
				@NonNull RtspProtoRscUrl rscUrlSubStream,
				@NonNull RtspProtoIdXsrc dummySsrcInbound,
				@NonNull RtspProtoIdXsrc ssrcOutbound,
				@NonNull RtspProtoKmdForSubStream kmdInbound
			) {
		/*
		 * In a DESCRIBE response we don't get the SSRC, RTP SeqNr and RTP Timestamp.
		 * Once we have made a SETUP request, we get the missing information in the response
		 * and need to update this information in the [RtspProtoSetupInfoForSubStream].
		 */
		createAndAddSetupDescribeSubStream(
				rscUrlSubStream,
				dummySsrcInbound,
				ssrcOutbound,
				RtspProtoRtpSeqNr.ofEmpty(),
				RtspProtoRtpTimestamp.ofEmpty(),
				TimestampMonotonic.ofEmpty(),
				kmdInbound,
				null
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean containsSiForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (! idSubStream1.isEmpty() && idSubStream.equals(idSubStream1)) {
			return true;
		}
		return (! idSubStream2.isEmpty() && idSubStream.equals(idSubStream2));
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean haveSetupForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (! idSubStream1.isEmpty() && idSubStream.equals(idSubStream1) && siSsPtr1 != null) {
			return siSsPtr1.getHaveSetup();
		}
		if (! idSubStream2.isEmpty() && idSubStream.equals(idSubStream2) && siSsPtr2 != null) {
			return siSsPtr2.getHaveSetup();
		}
		return false;
	}

	public Optional<RtspProtoSetupInfoForSubStream> getSiPtrBySubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (idSubStream.isEmpty()) {
			return Optional.empty();
		}
		if (! idSubStream1.isEmpty() && idSubStream.equals(idSubStream1)) {
			return Optional.ofNullable(siSsPtr1);
		}
		if (! idSubStream2.isEmpty() && idSubStream.equals(idSubStream2)) {
			return Optional.ofNullable(siSsPtr2);
		}
		return Optional.empty();
	}

	public Optional<RtspProtoRscUrl> getResourceUrlBySubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (idSubStream.isEmpty()) {
			return Optional.empty();
		}
		RtspProtoRscUrl resObj = null;
		if (! idSubStream1.isEmpty() && idSubStream.equals(idSubStream1)) {
			resObj = (siSsPtr1 == null ? null : siSsPtr1.getRscUrlSubStreamPtr());
		} else if (! idSubStream2.isEmpty() && idSubStream.equals(idSubStream2)) {
			resObj = (siSsPtr2 == null ? null : siSsPtr2.getRscUrlSubStreamPtr());
		}
		if (resObj == null) {
			return Optional.empty();
		}
		return Optional.of(resObj.clone());
	}

	public Optional<RtspProtoIdXsrc> getSsrcOutboundBySubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (idSubStream.isEmpty()) {
			return Optional.empty();
		}
		RtspProtoIdXsrc resObj = null;
		if (! idSubStream1.isEmpty() && idSubStream.equals(idSubStream1)) {
			resObj = (siSsPtr1 == null ? null : siSsPtr1.getSsrcOutboundPtr());
		} else if (! idSubStream2.isEmpty() && idSubStream.equals(idSubStream2)) {
			resObj = (siSsPtr2 == null ? null : siSsPtr2.getSsrcOutboundPtr());
		}
		if (resObj == null) {
			return Optional.empty();
		}
		return Optional.of(resObj.clone());
	}

	public int getNumberOfSubStreams() {
		return (! idSubStream1.isEmpty() ? 1 : 0) + (! idSubStream2.isEmpty() ? 1 : 0);
	}

	public @NonNull Set<@NonNull RtspProtoIdSubStream> getSubStreamIds() {
		Set<@NonNull RtspProtoIdSubStream> resSet = new HashSet<>();
		if (! idSubStream1.isEmpty()) {
			resSet.add(idSubStream1.clone());
		}
		if (! idSubStream2.isEmpty()) {
			resSet.add(idSubStream2.clone());
		}
		return resSet;
	}

	public @NonNull Set<RtspProtoRscUrl> getRscUrls() {
		Set<@NonNull RtspProtoRscUrl> resSet = new HashSet<>();
		if (! idSubStream1.isEmpty() && siSsPtr1 != null) {
			resSet.add(siSsPtr1.getRscUrlSubStreamPtr().clone());
		}
		if (! idSubStream2.isEmpty() && siSsPtr2 != null) {
			resSet.add(siSsPtr2.getRscUrlSubStreamPtr().clone());
		}
		return resSet;
	}

	public @NonNull Set<@NonNull RtspProtoSetupInfoForSubStream> getSiPtrs() {
		Set<@NonNull RtspProtoSetupInfoForSubStream> resSet = new HashSet<>();
		if (! idSubStream1.isEmpty() && siSsPtr1 != null) {
			resSet.add(siSsPtr1);
		}
		if (! idSubStream2.isEmpty() && siSsPtr2 != null) {
			resSet.add(siSsPtr2);
		}
		return resSet;
	}

	public @NonNull RtspProtoTcpChannelNr getHighestTcpChannelNrFromAllSubStreams() {
		int max = -1;
		if (! idSubStream1.isEmpty() && siSsPtr1 != null &&
				! siSsPtr1.getSubStreamTpPtr().getClientTcpChannRtcpPtr().isEmpty() &&
				siSsPtr1.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow() > max) {
			max = siSsPtr1.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow();
		}
		if (! idSubStream2.isEmpty() && siSsPtr2 != null &&
				! siSsPtr2.getSubStreamTpPtr().getClientTcpChannRtcpPtr().isEmpty() &&
				siSsPtr2.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow() > max) {
			max = siSsPtr2.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow();
		}
		if (max < 0) {
			return RtspProtoTcpChannelNr.ofEmpty();
		}
		try {
			return RtspProtoTcpChannelNr.of(max);
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
			return RtspProtoTcpChannelNr.ofEmpty();
		}
	}

	public void replaceSiForSubStream(@NonNull RtspProtoIdSubStream idSubStream, @NonNull RtspProtoSetupInfoForSubStream value) {
		if (! containsSiForSubStreamId(idSubStream)) {
			throw new IllegalArgumentException("Sub-Stream Info for ID '" +
					idSubStream.getIdStr().orElse("-unset-") + "' does not exist");
		}
		putInfoForSubStream(value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void copyFrom(@NonNull RtspProtoSetupInfosStream other) {
		idSubStream1.copyFrom(other.idSubStream1);
		siSsPtr1 = other.siSsPtr1;  // copy pointer since it contains UDP sockets
		idSubStream2.copyFrom(other.idSubStream2);
		siSsPtr2 = other.siSsPtr2;  // copy pointer since it contains UDP sockets
	}

	public void clear() {
		idSubStream1.clear();
		siSsPtr1 = null;
		idSubStream2.clear();
		siSsPtr2 = null;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		boolean have1 = (! idSubStream1.isEmpty());
		boolean have2 = (! idSubStream2.isEmpty());

		return getClass().getSimpleName() + " [" +
				"siSubStream1=" + (have1 ? siSsPtr1 : "-") +
				", subStreamId1=" + (have1 ? "'" + idSubStream1.getIdStr().orElseThrow() + "'" : "-") +
				", siSubStream2=" + (have2 ? siSsPtr2 : "-") +
				", subStreamId2=" + (have2 ? "'" + idSubStream2.getIdStr().orElseThrow() + "'" : "-") +
				"]";
	}

	@Override
	public RtspProtoSetupInfosStream clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void createAndAddSetupDescribeSubStream(
				@NonNull RtspProtoRscUrl rscUrlSubStream,
				@Nullable RtspProtoIdXsrc ssrcInbound,
				@NonNull RtspProtoIdXsrc ssrcOutbound,
				@NonNull RtspProtoRtpSeqNr rtpSeqNr,
				@NonNull RtspProtoRtpTimestamp rtpTimestamp,
				@NonNull TimestampMonotonic rtpTimestampGenMono,
				@Nullable RtspProtoKmdForSubStream kmdInbound,
				@Nullable RtspProtoKmdForSubStream kmdOutbound
			) {
		if (rscUrlSubStream.getUrlStr().isEmpty()) {
			throw new IllegalArgumentException("Resource URL must be set");
		}
		if (rscUrlSubStream.idInputSource.isEmpty()) {
			throw new IllegalArgumentException("Input Source ID must be set");
		}
		if (rscUrlSubStream.idSubStream.isEmpty()) {
			throw new IllegalArgumentException("Sub-Stream ID must be set");
		}

		RtspProtoIdXsrc tmpSsrcForKmd;
		if (kmdInbound != null && ssrcInbound != null) {
			tmpSsrcForKmd = ssrcInbound;
		} else if (kmdOutbound != null) {
			tmpSsrcForKmd = ssrcOutbound;
		} else {
			throw new IllegalArgumentException("SSRC must be set");
		}
		if (tmpSsrcForKmd.isEmpty()) {
			throw new IllegalArgumentException("SSRC must be set");
		}

		SrtxpKmd tmpKmd = null;
		if (kmdInbound != null && kmdInbound.isKmdSet()) {
			tmpKmd = kmdInbound.getKmd().orElseThrow();
		} else if (kmdOutbound != null && kmdOutbound.isKmdSet()) {
			tmpKmd = kmdOutbound.getKmd().orElseThrow();
		}
		if (tmpKmd != null && ! tmpKmd.getMetaIsForLegacySdes() && tmpKmd.ssrcId().isEmpty()) {
			throw new IllegalArgumentException("SSRC must be set for MIKEY KMDs");
		}

		RtspProtoSetupInfoForSubStream resObj = new RtspProtoSetupInfoForSubStream(
				rscUrlSubStream,
				ssrcInbound,
				ssrcOutbound,
				rtpSeqNr,
				rtpTimestamp,
				rtpTimestampGenMono
			);
		if (tmpKmd != null) {
			if (kmdInbound != null) {
				resObj.getKmdInboundCurPtr().setKmd(tmpKmd, rscUrlSubStream.idSubStream);
			} else {
				resObj.getKmdOutboundPtr().setKmd(tmpKmd, rscUrlSubStream.idSubStream);
			}
			resObj.getSubStreamTpPtr().setIsEncr(true);
		}
		//
		putInfoForSubStream(resObj);
	}

	private void putInfoForSubStream(@NonNull RtspProtoSetupInfoForSubStream siForSs) {
		if (siForSs.getRscUrlSubStreamPtr().idSubStream.isEmpty()) {
			throw new IllegalArgumentException("idSubStream cannot be empty");
		}
		boolean isTrg1;
		if (! idSubStream1.isEmpty() && idSubStream1.equals(siForSs.getRscUrlSubStreamPtr().idSubStream)) {
			isTrg1 = true;
		} else if (! idSubStream2.isEmpty() && idSubStream2.equals(siForSs.getRscUrlSubStreamPtr().idSubStream)) {
			isTrg1 = false;
		} else {
			isTrg1 = idSubStream1.isEmpty();
		}
		if (isTrg1) {
			siSsPtr1 = siForSs;
			idSubStream1.copyFrom(siForSs.getRscUrlSubStreamPtr().idSubStream);
		} else {
			siSsPtr2 = siForSs;
			idSubStream2.copyFrom(siForSs.getRscUrlSubStreamPtr().idSubStream);
		}
	}

}
