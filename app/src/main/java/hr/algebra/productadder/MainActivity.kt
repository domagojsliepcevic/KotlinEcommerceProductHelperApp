package hr.algebra.productadder

import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import hr.algebra.productadder.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    // View binding for the activity layout.
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    // Lists to store selected images and colors.
    private var selectedImages = mutableListOf<Uri>()
    private val selectedColors = mutableListOf<Int>()

    // Firebase storage reference and Firestore instance.
    private val productStorage = Firebase.storage.reference
    private val firestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Set up a click listener for the color picker button.
        binding.buttonColorPicker.setOnClickListener {
            // Show a color picker dialog when the button is clicked.
            ColorPickerDialog.Builder(this)
                .setTitle("Product color")
                .setPositiveButton("Select", object : ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.let {
                            // Add the selected color to the list and update UI.
                            selectedColors.add(it.color)
                            updateColors()
                        }
                    }
                })
                .setNegativeButton("Cancel") { colorPickerDialog, _ -> colorPickerDialog.dismiss() }
                .show()
        }

        // Register for activity result to select images from the device.
        val selectImagesActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = result.data

                if (intent?.clipData != null) {
                    // Multiple images were selected.
                    val count = intent.clipData?.itemCount ?: 0
                    (0 until count).forEach {
                        val imageUri = intent.clipData?.getItemAt(it)?.uri
                        imageUri?.let {
                            // Add each selected image URI to the list.
                            selectedImages.add(it)
                        }
                    }
                } else {
                    // Single image was selected.
                    val imageUri = intent?.data
                    imageUri?.let {
                        // Add the selected image URI to the list.
                        selectedImages.add(it)
                    }
                }
                // Update the UI to display the selected images count.
                updateImages()
            }
        }

        // Set up a click listener for the image picker button.
        binding.buttonImagesPicker.setOnClickListener {
            val intent = Intent(ACTION_GET_CONTENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.type = "image/*"
            // Launch the image selection activity.
            selectImagesActivityResult.launch(intent)
        }
    }

    // Function to update the selected images count in the UI.
    private fun updateImages() {
        binding.tvSelectedImages.text = selectedImages.size.toString()
    }

    // Function to update the selected colors in the UI.
    private fun updateColors() {
        var colors = ""
        selectedColors.forEach {
            // Convert selected colors to hexadecimal format.
            colors = "$colors ${Integer.toHexString(it)}"
        }
        // Update the UI to display the selected colors.
        binding.tvSelectedColors.text = colors
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the toolbar menu.
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.saveProduct) {
            // Validate product information before saving.
            val productValidation = validateInformation()
            if (!productValidation) {
                // Show a toast message if validation fails.
                Toast.makeText(this, "Check your inputs", Toast.LENGTH_SHORT).show()
                return false
            }

            // Save the product to Firebase Firestore.
            saveProduct()
        }
        return super.onOptionsItemSelected(item)
    }

    // Function to save the product data to Firebase Firestore.
    private fun saveProduct() {
        val name = binding.etName.text.toString().trim()
        val category = binding.etCategory.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()
        val offerPercentage = binding.etOfferPercentage.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val sizes = getSizes(binding.etSizes.text.toString().trim())
        val imagesByteArrays = getImagesByteArray()
        val images = mutableListOf<String>()

        // Execute the following code block in a background thread.
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main)
            {
                showLoading()
            }
            // Show a loading progress bar.

            try {
                async {
                    imagesByteArrays.forEach {
                        val id = UUID.randomUUID().toString()
                        launch {
                            val imageStorage = productStorage.child("products/images/$id")
                            // Upload each image to Firebase Storage.
                            val result = imageStorage.putBytes(it).await()
                            // Get the download URL for the uploaded image.
                            val downloadUrl = result.storage.downloadUrl.await().toString()
                            // Add the download URL to the list of images.
                            images.add(downloadUrl)
                        }
                    }
                }.await()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main)
                {
                    // Hide the loading progress bar in case of an exception.
                    hideLoading()
                }

            }

            // Create a Product object with the collected data.
            val product = Product(
                UUID.randomUUID().toString(),
                name,
                category,
                price.toFloat(),
                if (offerPercentage.isEmpty()) null else offerPercentage.toFloat(),
                if (description.isEmpty()) null else description,
                if (selectedColors.isEmpty()) null else selectedColors,
                sizes,
                images
            )

            // Add the product to the Firestore collection.
            firestore.collection("Products").add(product).addOnSuccessListener {
                // Hide the loading progress bar on success.
                hideLoading()
            }.addOnFailureListener {
                // Hide the loading progress bar and log any errors.
                hideLoading()
                Log.e("Error", it.message.toString())
            }
        }
    }

    // Function to hide the loading progress bar.
    private fun hideLoading() {
        binding.progressBar.visibility = View.INVISIBLE
    }

    // Function to show the loading progress bar.
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }

    // Function to convert selected images to byte arrays.
    private fun getImagesByteArray(): List<ByteArray> {
        val imagesByteArray = mutableListOf<ByteArray>()
        selectedImages.forEach {
            val stream = ByteArrayOutputStream()
            @Suppress("DEPRECATION") val imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
            if (imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                imagesByteArray.add(stream.toByteArray())
            }
        }
        return imagesByteArray
    }

    // Function to split and retrieve sizes from a string.
    private fun getSizes(sizesStr: String): List<String>? {
        if (sizesStr.isEmpty())
            return null
        val sizesList = sizesStr.split(",")
        return sizesList
    }

    // Function to validate input information.
    private fun validateInformation(): Boolean {
        if (binding.etPrice.text.toString().trim().isEmpty()) {
            return false
        }
        if (binding.etName.text.toString().trim().isEmpty()) {
            return false
        }
        if (selectedImages.isEmpty()) {
            return false
        }
        return true
    }
}
