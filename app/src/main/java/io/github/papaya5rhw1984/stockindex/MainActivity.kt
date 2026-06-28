package io.github.papaya5rhw1984.stockindex

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.papaya5rhw1984.stockindex.ui.AppTheme
import io.github.papaya5rhw1984.stockindex.ui.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StockApp()
                }
            }
        }
    }
}

/* ───────────────────────── 도메인 모델 ───────────────────────── */

private data class IndexDef(
    val symbol: String,   // Stooq 심볼 (예: ^spx)
    val koName: String,
    val region: String,
    val flag: String,
    val fallback: Double  // 오프라인 첫 실행용 placeholder(부정확)
)

// 세계 주요 증시 지수 카탈로그 — 검색/추가용. (Stooq 심볼; 일부는 차이로 미수신 가능)
private val CATALOG = listOf(
    IndexDef("^kospi", "코스피", "한국", "🇰🇷", 2700.0),
    IndexDef("^kosdq", "코스닥", "한국", "🇰🇷", 850.0),
    IndexDef("^ndq",   "나스닥 종합", "미국", "🇺🇸", 17500.0),
    IndexDef("^dji",   "다우존스", "미국", "🇺🇸", 39000.0),
    IndexDef("^spx",   "S&P 500", "미국", "🇺🇸", 5400.0),
    IndexDef("^nkx",   "닛케이 225", "일본", "🇯🇵", 38500.0),
    IndexDef("^ftm",   "FTSE 100", "영국", "🇬🇧", 8200.0),
    IndexDef("^dax",   "DAX", "독일", "🇩🇪", 18500.0),
    IndexDef("^cac",   "CAC 40", "프랑스", "🇫🇷", 7600.0),
    IndexDef("^aex",   "AEX", "네덜란드", "🇳🇱", 900.0),
    IndexDef("^smi",   "SMI", "스위스", "🇨🇭", 12000.0),
    IndexDef("^ibex",  "IBEX 35", "스페인", "🇪🇸", 11000.0),
    IndexDef("^stx50", "유로스톡스 50", "유럽", "🇪🇺", 5000.0),
    IndexDef("^hsi",   "항셍", "홍콩", "🇭🇰", 18000.0),
    IndexDef("^shc",   "상하이종합", "중국", "🇨🇳", 3000.0),
    IndexDef("^twse",  "가권", "대만", "🇹🇼", 22000.0),
    IndexDef("^snx",   "센섹스", "인도", "🇮🇳", 80000.0),
    IndexDef("^nsei",  "니프티 50", "인도", "🇮🇳", 24000.0),
    IndexDef("^sti",   "STI", "싱가포르", "🇸🇬", 3400.0),
    IndexDef("^axjo",  "ASX 200", "호주", "🇦🇺", 7800.0),
    IndexDef("^bvp",   "보베스파", "브라질", "🇧🇷", 125000.0),
    IndexDef("^tsx",   "S&P/TSX", "캐나다", "🇨🇦", 22000.0),
    IndexDef("^mex",   "IPC", "멕시코", "🇲🇽", 55000.0),
    IndexDef("^mrv",   "메르발", "아르헨티나", "🇦🇷", 1500000.0),
    IndexDef("^mcx",   "MOEX", "러시아", "🇷🇺", 3200.0),
    IndexDef("^rts",   "RTS", "러시아", "🇷🇺", 1100.0),
    IndexDef("^ftmib", "FTSE MIB", "이탈리아", "🇮🇹", 34000.0),
    IndexDef("^wig20", "WIG20", "폴란드", "🇵🇱", 2400.0),
    IndexDef("^omxs30","OMXS30", "스웨덴", "🇸🇪", 2500.0),
    IndexDef("^atx",   "ATX", "오스트리아", "🇦🇹", 3600.0),
    IndexDef("^bel20", "BEL 20", "벨기에", "🇧🇪", 4000.0),
    IndexDef("^xu100", "BIST 100", "튀르키예", "🇹🇷", 10000.0),
    IndexDef("^tasi",  "타다울", "사우디", "🇸🇦", 11500.0),
    IndexDef("^jalsh", "JSE 종합", "남아공", "🇿🇦", 80000.0),
    IndexDef("^jci",   "JCI", "인도네시아", "🇮🇩", 7300.0),
    IndexDef("^set",   "SET", "태국", "🇹🇭", 1300.0),
    IndexDef("^klci",  "KLCI", "말레이시아", "🇲🇾", 1600.0),
    IndexDef("^psei",  "PSEi", "필리핀", "🇵🇭", 6600.0),
    IndexDef("^vnindex","VN 지수", "베트남", "🇻🇳", 1280.0),
    IndexDef("^bux",   "BUX", "헝가리", "🇭🇺", 75000.0),
    IndexDef("^px",    "PX", "체코", "🇨🇿", 1600.0),
    IndexDef("^bet",   "BET", "루마니아", "🇷🇴", 17000.0),
    IndexDef("^psi20", "PSI 20", "포르투갈", "🇵🇹", 6500.0),
    IndexDef("^omxh25","OMXH 25", "핀란드", "🇫🇮", 4200.0),
    IndexDef("^omxc25","OMXC 25", "덴마크", "🇩🇰", 2200.0),
    IndexDef("^obx",   "OBX", "노르웨이", "🇳🇴", 1300.0),
    IndexDef("^iseq",  "ISEQ", "아일랜드", "🇮🇪", 9500.0),
    IndexDef("^atg",   "ASE 종합", "그리스", "🇬🇷", 1400.0),
    IndexDef("^nz50",  "NZX 50", "뉴질랜드", "🇳🇿", 12500.0),
    IndexDef("^ta125", "TA-125", "이스라엘", "🇮🇱", 2100.0),
    IndexDef("^kse",   "KSE 100", "파키스탄", "🇵🇰", 78000.0),
    IndexDef("^egx30", "EGX 30", "이집트", "🇪🇬", 28000.0),
    IndexDef("^ipsa",  "IPSA", "칠레", "🇨🇱", 6500.0)
)

private val CATALOG_BY_SYMBOL = CATALOG.associateBy { it.symbol }

// 심볼 → 정의. 카탈로그에 없으면(직접추가) 일반 카드로 표시.
private fun defFor(symbol: String): IndexDef =
    CATALOG_BY_SYMBOL[symbol] ?: IndexDef(symbol, symbol.removePrefix("^").uppercase(), "직접추가", "🏳️", 0.0)

// 기본 표시 목록(처음 설치 시) — 한·미·일
private val DEFAULT_SYMBOLS = listOf("^kospi", "^kosdq", "^ndq", "^dji", "^spx", "^nkx")

/** 표시할 지수 심볼 목록(순서 유지) 영구 저장 */
private object SelStore {
    private const val KEY = "selected"
    fun load(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences("stockindex", Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return DEFAULT_SYMBOLS
        // 카탈로그에 없는 사용자 직접추가 심볼도 유지
        val list = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return if (list.isEmpty()) DEFAULT_SYMBOLS else list
    }
    fun save(ctx: Context, syms: List<String>) {
        ctx.getSharedPreferences("stockindex", Context.MODE_PRIVATE)
            .edit().putString(KEY, syms.joinToString(",")).apply()
    }
}

// 한 지수의 시세 한 줄
private data class Quote(
    val symbol: String,
    val open: Double,
    val close: Double,
    val date: String,
    val time: String
) {
    // 등락% = (종가-시가)/시가*100  (※ Stooq 무키 응답에 전일종가 필드 없음 → 시가 기준 일중 등락. 한계 명시.)
    val changePct: Double get() = if (open > 0) (close - open) / open * 100.0 else 0.0
    val changeAbs: Double get() = close - open
}

private fun fmtNum(v: Double): String =
    if (v >= 1000) String.format(Locale.US, "%,.0f", v) else String.format(Locale.US, "%,.2f", v)

private fun fmtSigned(v: Double): String {
    val s = if (v >= 0) "+" else "-"
    val a = abs(v)
    return s + (if (a >= 1000) String.format(Locale.US, "%,.0f", a) else String.format(Locale.US, "%,.2f", a))
}

private fun fmtPct(v: Double): String =
    (if (v >= 0) "+" else "") + String.format(Locale.US, "%.2f", v) + "%"

private fun fmtSavedTime(unix: Long): String =
    if (unix <= 0) "—" else SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(unix * 1000))

/* ───────────────────────── 네트워크 (Stooq CSV) ───────────────────────── */

/**
 * Stooq 무료·무키 CSV.
 * https://stooq.com/q/l/?s=^kospi+^kosdq+^ndq+^dji+^spx+^nkx&f=sd2t2ohlcvn&e=csv
 * 응답: Symbol,Date,Time,Open,High,Low,Close,Volume,Name
 */
private fun fetchOnce(urlStr: String): Map<String, Quote>? {
    return try {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 13000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) StockIndex")
            setRequestProperty("Accept", "text/csv,text/plain,*/*")
        }
        val code = conn.responseCode
        val text = if (code in 200..299)
            conn.inputStream.bufferedReader().use { it.readText() } else null
        conn.disconnect()
        if (text == null) null else parseCsv(text)
    } catch (e: Exception) {
        null
    }
}

private fun fetchQuotes(symbols: List<String>): Map<String, Quote>? {
    if (symbols.isEmpty()) return null
    val syms = symbols.joinToString("+")
    val urlStr = "https://stooq.com/q/l/?s=$syms&f=sd2t2ohlcvn&e=csv"
    // 느린 응답 대비: 1회 재시도(호출 횟수 제한은 사용자 액션 기준이라 영향 없음)
    fetchOnce(urlStr)?.let { return it }
    try { Thread.sleep(700) } catch (e: InterruptedException) { }
    return fetchOnce(urlStr)
}

private fun parseCsv(text: String): Map<String, Quote>? {
    val lines = text.trim().lines().filter { it.isNotBlank() }
    if (lines.size < 2) return null
    val out = HashMap<String, Quote>()
    // 첫 줄은 헤더 — 건너뜀
    for (i in 1 until lines.size) {
        val c = lines[i].split(",")
        if (c.size < 7) continue
        val sym = c[0].trim().lowercase(Locale.US)
        val date = c[1].trim()
        val time = c[2].trim()
        val open = c[3].trim().toDoubleOrNull()
        val close = c[6].trim().toDoubleOrNull()
        if (open == null || close == null || open <= 0.0 || close <= 0.0) continue
        // 카탈로그와 매칭(심볼 소문자 비교)
        val def = CATALOG.firstOrNull { it.symbol.lowercase(Locale.US) == sym }
        val key = def?.symbol ?: sym
        out[key] = Quote(key, open, close, date, time)
    }
    return if (out.isEmpty()) null else out
}

/* ───────────────────────── 화면 ───────────────────────── */

@Composable
private fun StockApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("stockindex", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    // 60초 슬라이딩 윈도우 내 최대 2회 호출 (옵션변경 제외 — 이 앱은 옵션 없음)
    val refreshTimes = remember { mutableListOf<Long>() }

    var selected by remember { mutableStateOf(SelStore.load(ctx)) }   // 표시할 지수 심볼(순서)
    var manage by remember { mutableStateOf(false) }                  // 관리 화면 표시

    var quotes by remember {
        mutableStateOf(
            selected.associateWith { s ->
                val fb = CATALOG_BY_SYMBOL[s]?.fallback ?: 0.0
                Quote(s, fb, fb, "—", "—")
            }
        )
    }
    var live by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var lastUnix by remember { mutableStateOf(0L) }
    var note by remember { mutableStateOf("불러오는 중…") }

    fun toggleSel(sym: String) {
        selected = if (selected.contains(sym)) selected - sym else selected + sym
        SelStore.save(ctx, selected)
    }

    fun applyCache(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            val arr = o.getJSONObject("q")
            val m = HashMap<String, Quote>()
            val keys = arr.keys()
            while (keys.hasNext()) {
                val sym = keys.next()
                val q = arr.getJSONObject(sym)
                m[sym] = Quote(
                    sym,
                    q.optDouble("o", 0.0),
                    q.optDouble("c", 0.0),
                    q.optString("d", "—"),
                    q.optString("t", "—")
                )
            }
            if (m.isEmpty()) return false
            quotes = m
            lastUnix = o.optLong("saved", 0L)
            true
        } catch (e: Exception) { false }
    }

    fun refresh(silent: Boolean = false) {
        if (loading) return
        val now = System.currentTimeMillis()
        refreshTimes.removeAll { now - it > 60_000 }
        if (refreshTimes.size >= 2) {
            if (!silent) {
                val waitSec = ((60_000 - (now - refreshTimes.first())) / 1000) + 1
                Toast.makeText(ctx, "잠시 후 다시 시도해주세요 (${waitSec}초)", Toast.LENGTH_SHORT).show()
            }
            return
        }
        refreshTimes.add(now)
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { fetchQuotes(selected) }
            if (result != null) {
                quotes = result
                live = true
                lastUnix = System.currentTimeMillis() / 1000
                note = "실시간 · ${fmtSavedTime(lastUnix)} 기준"
                // 캐시 저장
                val o = JSONObject()
                val qo = JSONObject()
                result.forEach { (sym, q) ->
                    qo.put(sym, JSONObject()
                        .put("o", q.open).put("c", q.close)
                        .put("d", q.date).put("t", q.time))
                }
                o.put("q", qo).put("saved", lastUnix)
                prefs.edit().putString("cache", o.toString()).apply()
            } else {
                live = false
                note = if (lastUnix > 0) "${fmtSavedTime(lastUnix)} 기준"
                       else "새로고침을 눌러 불러오세요"
                if (!silent) Toast.makeText(ctx, "연결이 원활하지 않아요 · 다시 시도", Toast.LENGTH_SHORT).show()
            }
            loading = false
        }
    }

    // 최초 진입: 캐시 로드 후 1회 자동 갱신 + 켜둔 동안 60초마다 자동 갱신
    LaunchedEffect(Unit) {
        val cached = prefs.getString("cache", null)
        if (cached != null && applyCache(cached)) {
            note = if (lastUnix > 0) "${fmtSavedTime(lastUnix)} 기준" else "불러오는 중…"
        }
        refresh(silent = true)
        while (true) {
            delay(180_000)           // 3분마다 자동 갱신(지수엔 충분 · 일일 호출 한도 보호)
            refresh(silent = true)
        }
    }

    // 관리(검색·추가·삭제) 화면
    if (manage) {
        ManageScreen(
            selected = selected,
            onToggle = { toggleSel(it) },
            onClose = { manage = false; refresh(silent = true) }
        )
        return
    }

    val defs = selected.map { defFor(it) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        // 헤더
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("증시 나우", color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            StatusChip(live = live, loading = loading)
            Spacer(Modifier.weight(1f))
            // 관리(추가/삭제) 버튼
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Brand.SurfaceHi)
                    .clickable { manage = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("＋ 관리", color = Brand.AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(8.dp))
            // 새로고침 버튼
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Brand.SurfaceHi)
                    .clickable { refresh() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(if (loading) "..." else "새로고침",
                    color = Brand.AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        // 카드 리스트
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (defs.isEmpty()) {
                item {
                    Text(
                        "표시할 지수가 없어요 · 우측 상단 ‘＋ 관리’에서 추가하세요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(defs, key = { it.symbol }) { def ->
                val q = quotes[def.symbol]
                IndexCard(def, q)
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "※ 일중 등락(시가 대비)입니다 · 무료 시세 특성상 지연·오차 가능 · 표시 전용(매매·투자 조언 아님)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    "데이터: Stooq (무료) · 개인정보 수집 없음",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatusChip(live: Boolean, loading: Boolean) {
    val (dot, label) = when {
        loading -> Brand.Saved to "갱신중"
        live -> Brand.Live to "실시간"
        else -> Brand.Saved to "지연"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brand.SurfaceHi)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, color = dot, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IndexCard(def: IndexDef, q: Quote?) {
    val pct = q?.changePct ?: 0.0
    val abs = q?.changeAbs ?: 0.0
    val color = when {
        pct > 0.0 -> Brand.Up
        pct < 0.0 -> Brand.Down
        else -> Brand.Flat
    }
    val arrow = when {
        pct > 0.0 -> "▲"   // ▲
        pct < 0.0 -> "▼"   // ▼
        else -> "—"        // —
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Brand.Divider, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // 좌측: 깃발 + 이름
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(def.flag, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(def.koName, color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(2.dp))
            Text(def.region, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        // 우측: 현재값 + 등락
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (q != null) fmtNum(q.close) else "—",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(arrow, color = color, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    "${fmtSigned(abs)} (${fmtPct(pct)})",
                    color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/* ───────────────────────── 관리(검색·추가·삭제) 화면 ───────────────────────── */

@Composable
private fun ManageScreen(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    var query by remember { mutableStateOf("") }
    var customSym by remember { mutableStateOf("") }
    val q = query.trim()
    val filtered = if (q.isBlank()) CATALOG
        else CATALOG.filter {
            it.koName.contains(q) || it.region.contains(q) ||
                it.symbol.contains(q, ignoreCase = true)
        }
    // 카탈로그에 없는 직접추가 심볼(삭제 가능하게 목록 상단에)
    val customSelected = selected.filter { !CATALOG_BY_SYMBOL.containsKey(it) }.map { defFor(it) }
        .filter { q.isBlank() || it.symbol.contains(q, ignoreCase = true) || it.koName.contains(q, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("지수 관리", color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Brand.Accent)
                    .clickable { onClose() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("완료", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("나라·지수명으로 검색해 추가/삭제하세요 (${selected.size}개 표시 중)",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("예: 독일, 인도, FTSE") }
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 내가 직접 추가한(카탈로그 외) 심볼 — 삭제 가능하게 위에 표시
            items(customSelected, key = { "c_" + it.symbol }) { def ->
                ManageRow(def, true) { onToggle(def.symbol) }
            }
            items(filtered, key = { it.symbol }) { def ->
                ManageRow(def, selected.contains(def.symbol)) { onToggle(def.symbol) }
            }
            // 목록에 없는 지수: Stooq 심볼로 직접 추가
            item {
                Spacer(Modifier.height(14.dp))
                Text("목록에 없나요? Stooq 심볼로 직접 추가",
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customSym,
                        onValueChange = { customSym = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("예: ^nepse, ^jkse") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brand.Accent)
                            .clickable {
                                var s = customSym.trim().lowercase()
                                if (s.isNotEmpty()) {
                                    if (!s.startsWith("^")) s = "^$s"
                                    if (!selected.contains(s)) onToggle(s)
                                    customSym = ""
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) { Text("추가", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(4.dp))
                Text("※ Stooq에 있는 지수만 값이 떠요. 심볼은 stooq.com에서 확인하세요. 값이 안 뜨면 다시 눌러 삭제.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ManageRow(def: IndexDef, on: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Brand.Divider, RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(def.flag, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(def.koName, color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(def.region, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (on) Brand.Accent else Brand.SurfaceHi)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                if (on) "표시중 ✓" else "＋ 추가",
                color = if (on) Color.White else Brand.AccentSoft,
                fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}
