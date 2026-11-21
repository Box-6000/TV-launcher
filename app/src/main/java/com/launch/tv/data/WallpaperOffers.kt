package com.launch.tv.data

data class WallpaperOffer(val title: String, val site: String, val path: String)

object WallpaperOffers {
    val widths = listOf(1920 to "Full HD", 2560 to "2K", 3840 to "4K")

    val all = listOf(
        WallpaperOffer("Лес", "Unsplash", "photo-1448375240586-882707db888b"),
        WallpaperOffer("Горы", "Unsplash", "photo-1464822759023-fed622ff2c3b"),
        WallpaperOffer("Океан", "Unsplash", "photo-1507525428034-b723cf961d3e"),
        WallpaperOffer("Ночное небо", "Unsplash", "photo-1419242902214-272b3f66ee7a"),
        WallpaperOffer("Город", "Unsplash", "photo-1480714378408-67cf0d13bc1b"),
        WallpaperOffer("Закат", "Unsplash", "photo-1495616811223-4d98c6e9c869"),
        WallpaperOffer("Туманный лес", "Pexels", "1671325"),
        WallpaperOffer("Горное озеро", "Pexels", "417074"),
        WallpaperOffer("Берег", "Pexels", "1001682"),
        WallpaperOffer("Ночной город", "Pexels", "316093"),
        WallpaperOffer("Сияние", "Pexels", "1434608"),
        WallpaperOffer("Звёзды", "Pexels", "1252890"),
    )

    fun url(offer: WallpaperOffer, width: Int): String {
        return if (offer.site == "Pexels") {
            "https://images.pexels.com/photos/${offer.path}/pexels-photo-${offer.path}.jpeg?auto=compress&cs=tinysrgb&w=$width"
        } else {
            "https://images.unsplash.com/${offer.path}?auto=format&fit=max&w=$width&q=85"
        }
    }
}
