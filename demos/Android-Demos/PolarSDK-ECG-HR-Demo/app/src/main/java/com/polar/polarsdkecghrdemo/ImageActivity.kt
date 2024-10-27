package com.polar.polarsdkecghrdemo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

/* The image generation is far from being finished, the recordings would be processed (filtered and interpolated)
in the app, which is not implemented, as well as the switching prompting system and saving of the images.
The prompting itself would actually work. If the commented-out code is adopted. */

/* val intent = Intent(this, ImageActivity::class.java)
startActivity(intent) */

class ImageActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "Polar_ImageActivity"
        //private val OPENAI_API_KEY = SecretKey().getKey() => Implement a class SecretKey with a getter
    }

    private lateinit var imageViewGeneratedImages: ImageView
    private lateinit var imageTestButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_images)
        Log.d(TAG, "ImageActivity started")

        imageViewGeneratedImages = findViewById(R.id.generated_image)
        imageTestButton = findViewById(R.id.buttonTestImage)

        imageTestButton.setOnClickListener {
            imageViewGeneratedImages.setImageResource(R.mipmap.a_landscape_filled_with_life_and_good_weather)
        }

        /* Code for image generation and displaying it.

        val config = OpenAIConfig(
            token = OPENAI_API_KEY,
            timeout = Timeout(socket = 60.seconds)
            // additional configurations possible...
        )

        val openAI = OpenAI(config)

        imageTestButton.setOnClickListener {
            val requestReceived = "Processing..."
            imageTestButton.text = requestReceived
            try {
                runBlocking{
                        val result = withContext(Dispatchers.IO) {
                            // Alternatively, I could use openAI.imageJSON to get the image as JSON.
                            openAI.imageURL(
                                creation = ImageCreation(
                                    prompt = "A landscape filled with life and good weather.",
                                    model = ModelId("dall-e-3"),
                                    n = 1,
                                    // DALL-E 3 only supports images of size 1024x1024, 1024x1792 or 1792x1024 pixels.
                                    size = ImageSize.is1024x1024
                                    // style = "natural possible, images are "vivid" by default
                                    // quality = "hd" possible, images are "normal" quality by default
                                )
                            ).first().url
                        }
                    Picasso.get().load(result).resize(1024, 1024).into(imageViewGeneratedImages)
                    Log.i(TAG, result)
                    val normalText = resources.getText(R.string.generate_images)
                    imageTestButton.text = normalText
                }
            } catch(e: InterruptedException) {
                e.printStackTrace()
            }
        }
        */
    }

    public override fun onDestroy() {
        super.onDestroy()
    }
}