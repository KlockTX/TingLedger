package com.klocktx.moneyblocks.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/** 预设矢量图标库，key 存进数据库，改名换图标都不影响历史条目。 */
object BillIcons {

    val choices: List<Pair<String, ImageVector>> = listOf(
        "restaurant" to Icons.Filled.Restaurant,
        "local_taxi" to Icons.Filled.LocalTaxi,
        "bolt" to Icons.Filled.Bolt,
        "shopping_cart" to Icons.Filled.ShoppingCart,
        "local_cafe" to Icons.Filled.LocalCafe,
        "local_movies" to Icons.Filled.LocalMovies,
        "local_hospital" to Icons.Filled.LocalHospital,
        "school" to Icons.Filled.School,
        "flight" to Icons.Filled.Flight,
        "home" to Icons.Filled.Home,
        "checkroom" to Icons.Filled.Checkroom,
        "local_gas_station" to Icons.Filled.LocalGasStation,
        "pets" to Icons.Filled.Pets,
        "card_giftcard" to Icons.Filled.CardGiftcard,
        "phone" to Icons.Filled.Phone,
        "ondemand_video" to Icons.Filled.OndemandVideo,
        "money" to Icons.Filled.Money,
        "directions_transit" to Icons.Filled.DirectionsTransit,
        "wifi" to Icons.Filled.Wifi,
        "local_bar" to Icons.Filled.LocalBar,
        "local_grocery_store" to Icons.Filled.LocalGroceryStore,
        "local_parking" to Icons.Filled.LocalParking,
        "menu_book" to Icons.AutoMirrored.Filled.MenuBook,
        "fitness_center" to Icons.Filled.FitnessCenter,
        "category" to Icons.Filled.Category,
    )

    private val byKey: Map<String, ImageVector> = choices.toMap()

    fun icon(key: String): ImageVector = byKey[key] ?: Icons.Filled.Category

    const val FALLBACK_KEY = "category"
}
