import androidx.test.core.app.ApplicationProvider
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarBleSdkInstanceException
import com.polar.sdk.impl.BDBleApiImpl
import org.junit.Assert
import org.junit.Test

class BDBleApiImplTest {
    @Test
    fun singletonInstanceForPolarBleSDK() {

        // Arrange
        val polarBleApiDefaultInstance =
            BDBleApiImpl.getInstance(
                ApplicationProvider.getApplicationContext(),
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)
            )

        // Act
        val polarBleApiSecondInstance =
            BDBleApiImpl.getInstance(
                ApplicationProvider.getApplicationContext(),
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)
            )

        // Assert
        Assert.assertEquals(polarBleApiDefaultInstance, polarBleApiSecondInstance)
    }

    @Test
    fun singletonInstanceNotPossibleIfDifferentFeaturesRequired() {

        // Arrange
        BDBleApiImpl.getInstance(
            ApplicationProvider.getApplicationContext(),
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)
        )

        // Act && Assert
        Assert.assertThrows(PolarBleSdkInstanceException::class.java) {
            BDBleApiImpl.getInstance(
                ApplicationProvider.getApplicationContext(),
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER)
            )
        }
    }
}
