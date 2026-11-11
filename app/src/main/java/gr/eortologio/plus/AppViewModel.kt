package gr.eortologio.plus

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

sealed interface OtaState {
    data object Idle : OtaState
    data object Checking : OtaState
    data class UpToDate(val version: String) : OtaState
    data class Available(val release: OtaUpdater.Release, val installed: String) : OtaState
    data class NeedPermission(val release: OtaUpdater.Release) : OtaState
    data class Downloading(val percent: Int) : OtaState
    data class Error(val message: String) : OtaState
}

/** Κατάσταση της οθόνης (επιβιώνει σε περιστροφή / αλλαγή θέματος). */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    var page by mutableStateOf("today")
    var query by mutableStateOf("")
    var month by mutableStateOf(YearMonth.now())
    var selected by mutableStateOf(LocalDate.now())
    /** Αλλάζει όταν πρέπει να ξαναδιαβαστούν τα δεδομένα (επιστροφή στην εφαρμογή, αποθήκευση, άδειες…). */
    var rev by mutableIntStateOf(0)
        private set
    var ota by mutableStateOf<OtaState>(OtaState.Idle)

    fun refresh() { rev++ }

    fun go(p: String) {
        page = p
        query = ""
    }

    fun openDay(d: LocalDate) {
        page = "calendar"
        month = YearMonth.from(d)
        selected = d
        query = ""
    }

    fun checkOta(manual: Boolean) {
        val ctx = getApplication<Application>()
        if (ota is OtaState.Checking || ota is OtaState.Downloading) return
        if (!manual && !OtaUpdater.autoCheckDue(ctx)) return
        if (manual) ota = OtaState.Checking
        viewModelScope.launch {
            ota = try {
                val r = OtaUpdater.fetchLatest(ctx)
                val mine = OtaUpdater.currentVersion(ctx)
                if (OtaUpdater.compare(r.tag, mine) > 0) OtaState.Available(r, mine)
                else if (manual) OtaState.UpToDate(mine) else OtaState.Idle
            } catch (e: Exception) {
                if (manual) OtaState.Error(e.message ?: e.javaClass.simpleName) else OtaState.Idle
            }
        }
    }

    fun startUpdate(r: OtaUpdater.Release) {
        val ctx = getApplication<Application>()
        val url = r.apkUrl ?: return
        if (!OtaUpdater.canInstall(ctx)) { ota = OtaState.NeedPermission(r); return }
        ota = OtaState.Downloading(0)
        viewModelScope.launch {
            ota = try {
                OtaUpdater.download(ctx, url) { ota = OtaState.Downloading(it) }
                OtaUpdater.install(ctx)
                OtaState.Idle
            } catch (e: Exception) {
                OtaState.Error(T.t("Η λήψη απέτυχε: ") + (e.message ?: ""))
            }
        }
    }
}
