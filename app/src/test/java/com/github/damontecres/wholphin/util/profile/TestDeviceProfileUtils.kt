package com.github.damontecres.wholphin.util.profile

import io.mockk.every
import io.mockk.mockk
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that forceEnabledHdr/forceDisabledHdr correctly override the device's own
 * codec-capability detection when building the unsupported video range type list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class TestDeviceProfileUtils {
    private fun unsupportedRangeTypes(
        profile: DeviceProfile,
        codec: String,
    ): Set<String> =
        profile.codecProfiles
            .filter { it.type == CodecType.VIDEO && it.codec == codec }
            .firstNotNullOf { codecProfile ->
                codecProfile.applyConditions.firstOrNull { it.property == ProfileConditionValue.VIDEO_RANGE_TYPE }?.value
            }.split("|")
            .toSet()

    private fun buildProfile(
        mediaTest: MediaCodecCapabilitiesTest,
        jellyfinTenEleven: Boolean = false,
        decodeAv1: Boolean = false,
        forceEnabledHdr: Set<String> = emptySet(),
        forceDisabledHdr: Set<String> = emptySet(),
    ): DeviceProfile =
        createDeviceProfile(
            mediaTest = mediaTest,
            maxBitrate = 100_000_000,
            isAC3Enabled = true,
            downMixAudio = false,
            assDirectPlay = true,
            pgsDirectPlay = true,
            decodeAv1 = decodeAv1,
            jellyfinTenEleven = jellyfinTenEleven,
            preferAc3ForSurround = false,
            forceEnabledHdr = forceEnabledHdr,
            forceDisabledHdr = forceDisabledHdr,
            maxResolution = 0,
        )

    @Test
    fun `Test HEVC Profile 7 unsupported when device does not support it`() {
        val mediaTest = mockk<MediaCodecCapabilitiesTest>(relaxed = true)
        every { mediaTest.supportsHevcDolbyVisionEL() } returns false
        every { mediaTest.supportsHevcDolbyVision() } returns true
        every { mediaTest.supportsHevcHDR10Plus() } returns true

        val profile = buildProfile(mediaTest, jellyfinTenEleven = true)
        Assert.assertTrue(unsupportedRangeTypes(profile, Codec.Video.HEVC).contains(WholphinVideoRangeType.DOVI_WITH_EL.serialName))
    }

    @Test
    fun `Test force enable overrides HEVC Profile 7 device support check`() {
        val mediaTest = mockk<MediaCodecCapabilitiesTest>(relaxed = true)
        every { mediaTest.supportsHevcDolbyVisionEL() } returns false
        every { mediaTest.supportsHevcDolbyVision() } returns true
        every { mediaTest.supportsHevcHDR10Plus() } returns true

        val profile =
            buildProfile(
                mediaTest,
                jellyfinTenEleven = true,
                forceEnabledHdr =
                    setOf(
                        WholphinVideoRangeType.DOVI_WITH_EL.serialName,
                        WholphinVideoRangeType.DOVI_WITH_EL_HDR10_PLUS.serialName,
                    ),
            )
        Assert.assertFalse(unsupportedRangeTypes(profile, Codec.Video.HEVC).contains(WholphinVideoRangeType.DOVI_WITH_EL.serialName))
    }

    @Test
    fun `Test force disable overrides HEVC Profile 7 device support check`() {
        val mediaTest = mockk<MediaCodecCapabilitiesTest>(relaxed = true)
        every { mediaTest.supportsHevcDolbyVisionEL() } returns true
        every { mediaTest.supportsHevcDolbyVision() } returns true
        every { mediaTest.supportsHevcHDR10Plus() } returns true

        val profile =
            buildProfile(
                mediaTest,
                jellyfinTenEleven = true,
                forceDisabledHdr =
                    setOf(
                        WholphinVideoRangeType.DOVI_WITH_EL.serialName,
                        WholphinVideoRangeType.DOVI_WITH_EL_HDR10_PLUS.serialName,
                    ),
            )
        Assert.assertTrue(unsupportedRangeTypes(profile, Codec.Video.HEVC).contains(WholphinVideoRangeType.DOVI_WITH_EL.serialName))
    }

    @Test
    fun `Test AV1 Profile 5 unsupported when device does not support it`() {
        val mediaTest = mockk<MediaCodecCapabilitiesTest>(relaxed = true)
        every { mediaTest.supportsAV1DolbyVision() } returns false

        val profile = buildProfile(mediaTest, decodeAv1 = false)
        Assert.assertTrue(unsupportedRangeTypes(profile, Codec.Video.AV1).contains(WholphinVideoRangeType.DOVI.serialName))
    }

    @Test
    fun `Test force enable overrides AV1 Profile 5 device support check`() {
        val mediaTest = mockk<MediaCodecCapabilitiesTest>(relaxed = true)
        every { mediaTest.supportsAV1DolbyVision() } returns false

        val profile =
            buildProfile(
                mediaTest,
                decodeAv1 = false,
                forceEnabledHdr = setOf(WholphinVideoRangeType.DOVI.serialName),
            )
        Assert.assertFalse(unsupportedRangeTypes(profile, Codec.Video.AV1).contains(WholphinVideoRangeType.DOVI.serialName))
    }

    @Test
    fun `Test force disable overrides AV1 Profile 5 device support check`() {
        val mediaTest = mockk<MediaCodecCapabilitiesTest>(relaxed = true)
        every { mediaTest.supportsAV1DolbyVision() } returns true

        val profile =
            buildProfile(
                mediaTest,
                decodeAv1 = false,
                forceDisabledHdr = setOf(WholphinVideoRangeType.DOVI.serialName),
            )
        Assert.assertTrue(unsupportedRangeTypes(profile, Codec.Video.AV1).contains(WholphinVideoRangeType.DOVI.serialName))
    }

    @Test
    fun `HLS transcodes only ask for sound the player can hear in TS`() {
        val mediaTest = mockk<MediaCodecCapabilitiesTest>(relaxed = true)
        val profile = buildProfile(mediaTest)
        val video = profile.transcodingProfiles.first { it.type == DlnaProfileType.VIDEO }
        val codecs = video.audioCodec.split(",")
        Assert.assertEquals(Codec.Audio.AAC, codecs.first())
        listOf(Codec.Audio.FLAC, Codec.Audio.OPUS, Codec.Audio.TRUEHD, Codec.Audio.ALAC, Codec.Audio.VORBIS).forEach {
            Assert.assertFalse("$it should not be in ${video.audioCodec}", it in codecs)
        }
        Assert.assertTrue(Codec.Audio.EAC3 in codecs)
    }
}
