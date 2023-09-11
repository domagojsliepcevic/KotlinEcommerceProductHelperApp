package hr.algebra.productadder

/**
 * Data class representing a product.
 *
 * @param id: Unique identifier for the product.
 * @param name: Name of the product.
 * @param category: Category of the product.
 * @param price: Price of the product.
 * @param offerPercentage: Optional offer percentage for the product.
 * @param description: Optional description of the product.
 * @param colors: Optional list of color options for the product.
 * @param sizes: Optional list of size options for the product.
 * @param images: List of image URLs representing the product.
 */
data class Product(
    val id: String,
    val name: String,
    val category: String,
    val price: Float,
    val offerPercentage: Float? = null,
    val description: String? = null,
    val colors: List<Int>? = null,
    val sizes: List<String>? = null,
    val images: List<String>
)