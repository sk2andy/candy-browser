package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.TextUnit

@Composable
actual fun PlatformProfileEmoji(
    emoji: String,
    fontSize: TextUnit,
    modifier: Modifier,
) {
    Icon(
        imageVector = profileIcon(emoji),
        contentDescription = null,
        modifier = modifier,
    )
}

private fun profileIcon(emoji: String): ImageVector = when (emoji) {
    "🍬" -> Icons.Filled.Celebration
    "⭐" -> Icons.Filled.Star
    "💼" -> Icons.Filled.Work
    "🛒" -> Icons.Filled.ShoppingCart
    "🎮" -> Icons.Filled.SportsEsports
    "📚", "📖" -> Icons.Filled.MenuBook
    "✈️" -> Icons.Filled.Flight
    "🏠" -> Icons.Filled.Home
    "🎵" -> Icons.Filled.MusicNote
    "🧪" -> Icons.Filled.Science
    "📰" -> Icons.Filled.Newspaper
    "❤️" -> Icons.Filled.Favorite
    "🔥" -> Icons.Filled.LocalFireDepartment
    "🌙" -> Icons.Filled.DarkMode
    "🌿" -> Icons.Filled.Eco
    "🎨" -> Icons.Filled.Palette
    "🏋️" -> Icons.Filled.FitnessCenter
    "💡" -> Icons.Filled.Lightbulb
    "🏫", "🎓", "🧑‍🎓" -> Icons.Filled.School
    "🎒" -> Icons.Filled.Backpack
    "✏️" -> Icons.Filled.Edit
    "👶" -> Icons.Filled.ChildCare
    "🧸" -> Icons.Filled.Gamepad
    "🍼" -> Icons.Filled.LocalDrink
    "👨‍👩‍👧" -> Icons.Filled.FamilyRestroom
    "💍" -> Icons.Filled.Diamond
    "💒" -> Icons.Filled.Church
    "💰" -> Icons.Filled.Savings
    "💳" -> Icons.Filled.CreditCard
    "🪙" -> Icons.Filled.Paid
    "📈" -> Icons.Filled.TrendingUp
    "🎬" -> Icons.Filled.Theaters
    "🍿" -> Icons.Filled.LocalMovies
    "📺" -> Icons.Filled.Tv
    "📷" -> Icons.Filled.CameraAlt
    "💻" -> Icons.Filled.Laptop
    "📱" -> Icons.Filled.PhoneIphone
    "🚗" -> Icons.Filled.DirectionsCar
    "🚲" -> Icons.Filled.DirectionsBike
    "⚽" -> Icons.Filled.SportsSoccer
    "🏀" -> Icons.Filled.SportsBasketball
    "🏖️" -> Icons.Filled.BeachAccess
    "🍕" -> Icons.Filled.LocalPizza
    "☕" -> Icons.Filled.LocalCafe
    "🎉" -> Icons.Filled.Celebration
    "🎁" -> Icons.Filled.CardGiftcard
    "🐶", "🐱" -> Icons.Filled.Pets
    "🌍" -> Icons.Filled.Public
    "🩺" -> Icons.Filled.MedicalServices
    "📅" -> Icons.Filled.CalendarMonth
    else -> Icons.Filled.Business
}
