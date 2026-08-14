package com.newsbrief.data

import java.text.DecimalFormat

/** 환율 계산기에 올릴 수 있는 통화 개수. 너무 많으면 한 줄씩 다시 계산하는 게 느려진다. */
const val MAX_CURRENCIES = 10

/**
 * [symbol] 은 달러·엔처럼 겹치는 기호를 나라와 함께 적어 구분한다 (US$, CA$, JP¥, CN¥).
 * [decimals] 는 통화마다 다르다 — 엔·원·동은 소수점을 쓰지 않고, 대부분은 두 자리다.
 */
data class CurrencyInfo(
    val code: String,
    val name: String,
    val flag: String,
    val symbol: String,
    val decimals: Int = 2,
)

val CURRENCIES: List<CurrencyInfo> = listOf(
    CurrencyInfo("KRW", "대한민국 원", "🇰🇷", "₩", 0),
    CurrencyInfo("USD", "미국 달러", "🇺🇸", "US$"),
    CurrencyInfo("JPY", "일본 엔", "🇯🇵", "JP¥", 0),
    CurrencyInfo("EUR", "유로", "🇪🇺", "€"),
    CurrencyInfo("CNY", "중국 위안", "🇨🇳", "CN¥"),
    CurrencyInfo("GBP", "영국 파운드", "🇬🇧", "£"),
    CurrencyInfo("HKD", "홍콩 달러", "🇭🇰", "HK$"),
    CurrencyInfo("TWD", "대만 달러", "🇹🇼", "NT$"),
    CurrencyInfo("SGD", "싱가포르 달러", "🇸🇬", "S$"),
    CurrencyInfo("THB", "태국 바트", "🇹🇭", "฿"),
    CurrencyInfo("VND", "베트남 동", "🇻🇳", "₫", 0),
    CurrencyInfo("PHP", "필리핀 페소", "🇵🇭", "₱"),
    CurrencyInfo("IDR", "인도네시아 루피아", "🇮🇩", "Rp", 0),
    CurrencyInfo("MYR", "말레이시아 링깃", "🇲🇾", "RM"),
    CurrencyInfo("INR", "인도 루피", "🇮🇳", "₹"),
    CurrencyInfo("AUD", "호주 달러", "🇦🇺", "AU$"),
    CurrencyInfo("NZD", "뉴질랜드 달러", "🇳🇿", "NZ$"),
    CurrencyInfo("CAD", "캐나다 달러", "🇨🇦", "CA$"),
    CurrencyInfo("CHF", "스위스 프랑", "🇨🇭", "CHF"),
    CurrencyInfo("SEK", "스웨덴 크로나", "🇸🇪", "kr"),
    CurrencyInfo("NOK", "노르웨이 크로네", "🇳🇴", "kr"),
    CurrencyInfo("DKK", "덴마크 크로네", "🇩🇰", "kr"),
    CurrencyInfo("ISK", "아이슬란드 크로나", "🇮🇸", "kr", 0),
    CurrencyInfo("RUB", "러시아 루블", "🇷🇺", "₽"),
    CurrencyInfo("TRY", "튀르키예 리라", "🇹🇷", "₺"),
    CurrencyInfo("PLN", "폴란드 즈워티", "🇵🇱", "zł"),
    CurrencyInfo("CZK", "체코 코루나", "🇨🇿", "Kč"),
    CurrencyInfo("HUF", "헝가리 포린트", "🇭🇺", "Ft", 0),
    CurrencyInfo("RON", "루마니아 레우", "🇷🇴", "lei"),
    CurrencyInfo("BGN", "불가리아 레프", "🇧🇬", "лв"),
    CurrencyInfo("UAH", "우크라이나 흐리우냐", "🇺🇦", "₴"),
    CurrencyInfo("BRL", "브라질 헤알", "🇧🇷", "R$"),
    CurrencyInfo("MXN", "멕시코 페소", "🇲🇽", "MX$"),
    CurrencyInfo("ARS", "아르헨티나 페소", "🇦🇷", "AR$"),
    CurrencyInfo("CLP", "칠레 페소", "🇨🇱", "CL$", 0),
    CurrencyInfo("COP", "콜롬비아 페소", "🇨🇴", "CO$", 0),
    CurrencyInfo("PEN", "페루 솔", "🇵🇪", "S/"),
    CurrencyInfo("ZAR", "남아프리카 랜드", "🇿🇦", "R"),
    CurrencyInfo("EGP", "이집트 파운드", "🇪🇬", "E£"),
    CurrencyInfo("NGN", "나이지리아 나이라", "🇳🇬", "₦"),
    CurrencyInfo("KES", "케냐 실링", "🇰🇪", "KSh"),
    CurrencyInfo("MAD", "모로코 디르함", "🇲🇦", "MAD"),
    CurrencyInfo("GHS", "가나 시디", "🇬🇭", "₵"),
    CurrencyInfo("AED", "아랍에미리트 디르함", "🇦🇪", "AED"),
    CurrencyInfo("SAR", "사우디 리얄", "🇸🇦", "SR"),
    CurrencyInfo("QAR", "카타르 리얄", "🇶🇦", "QR"),
    CurrencyInfo("KWD", "쿠웨이트 디나르", "🇰🇼", "KD", 3),
    CurrencyInfo("BHD", "바레인 디나르", "🇧🇭", "BD", 3),
    CurrencyInfo("OMR", "오만 리알", "🇴🇲", "OMR", 3),
    CurrencyInfo("JOD", "요르단 디나르", "🇯🇴", "JD", 3),
    CurrencyInfo("ILS", "이스라엘 셰켈", "🇮🇱", "₪"),
    CurrencyInfo("PKR", "파키스탄 루피", "🇵🇰", "Rs"),
    CurrencyInfo("BDT", "방글라데시 타카", "🇧🇩", "৳"),
    CurrencyInfo("LKR", "스리랑카 루피", "🇱🇰", "Rs"),
    CurrencyInfo("NPR", "네팔 루피", "🇳🇵", "Rs"),
    CurrencyInfo("KHR", "캄보디아 리엘", "🇰🇭", "៛", 0),
    CurrencyInfo("LAK", "라오스 킵", "🇱🇦", "₭", 0),
    CurrencyInfo("MMK", "미얀마 짯", "🇲🇲", "K", 0),
    CurrencyInfo("MNT", "몽골 투그릭", "🇲🇳", "₮", 0),
    CurrencyInfo("KZT", "카자흐스탄 텡게", "🇰🇿", "₸"),
    CurrencyInfo("UZS", "우즈베키스탄 숨", "🇺🇿", "soʻm", 0),
    CurrencyInfo("MOP", "마카오 파타카", "🇲🇴", "MOP$"),
    CurrencyInfo("BND", "브루나이 달러", "🇧🇳", "B$"),
    CurrencyInfo("FJD", "피지 달러", "🇫🇯", "FJ$"),
    CurrencyInfo("XPF", "CFP 프랑", "🇵🇫", "₣", 0),
    CurrencyInfo("GTQ", "과테말라 케찰", "🇬🇹", "Q"),
    CurrencyInfo("DOP", "도미니카 페소", "🇩🇴", "RD$"),
    CurrencyInfo("CRC", "코스타리카 콜론", "🇨🇷", "₡"),
    CurrencyInfo("UYU", "우루과이 페소", "🇺🇾", "UY$"),
    CurrencyInfo("RSD", "세르비아 디나르", "🇷🇸", "din"),
    CurrencyInfo("ETB", "에티오피아 비르", "🇪🇹", "Br"),
    CurrencyInfo("TZS", "탄자니아 실링", "🇹🇿", "TSh", 0),
    CurrencyInfo("XOF", "서아프리카 CFA 프랑", "🇸🇳", "CFA", 0),
    CurrencyInfo("XAF", "중앙아프리카 CFA 프랑", "🇨🇲", "FCFA", 0),
)

private val byCode = CURRENCIES.associateBy { it.code }

fun currencyOf(code: String): CurrencyInfo =
    byCode[code] ?: CurrencyInfo(code, code, "🏳️", code)

private val formatters = HashMap<Int, DecimalFormat>()

/** 천 단위 쉼표를 넣고, 통화별 소수점 자리수에 맞춘다. */
fun formatAmount(value: Double, decimals: Int): String {
    val format = formatters.getOrPut(decimals) {
        DecimalFormat(if (decimals == 0) "#,##0" else "#,##0." + "0".repeat(decimals))
    }
    return format.format(value)
}

/**
 * 통화 목록을 가나다 묶음으로 나눌 때 쓰는 첫 글자.
 * 한글이 아니면 (CFP 프랑 등) 첫 글자를 그대로 쓴다.
 */
fun groupKeyOf(name: String): String = name.firstOrNull()?.toString().orEmpty()
