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
                PolarBleApi.ALL_FEATURES
            )

        // Act
        val polarBleApiSecondInstance =
            BDBleApiImpl.getInstance(
                ApplicationProvider.getApplicationContext(),
                PolarBleApi.ALL_FEATURES
            )

        // Assert
        Assert.assertEquals(polarBleApiDefaultInstance, polarBleApiSecondInstance)
    }

    @Test
    fun singletonInstanceNotPossibleIfDifferentFeaturesRequired() {

        // Arrange
        BDBleApiImpl.getInstance(
            ApplicationProvider.getApplicationContext(),
            PolarBleApi.ALL_FEATURES
        )

        // Act && Assert
        Assert.assertThrows(PolarBleSdkInstanceException::class.java) {
            BDBleApiImpl.getInstance(
                ApplicationProvider.getApplicationContext(),
                PolarBleApi.FEATURE_POLAR_FILE_TRANSFER
            )
        }
    }
}