package tk.zwander.rootactivitylauncher.data.model

import com.ensody.reactivestate.derived
import com.ensody.reactivestate.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import tk.zwander.rootactivitylauncher.data.FilterMode
import tk.zwander.rootactivitylauncher.util.AdvancedSearcher
import tk.zwander.rootactivitylauncher.util.forEachParallel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.PatternSyntaxException

object MainModel {
    val apps = MutableStateFlow<List<AppModel>>(listOf())
    val filteredApps = MutableStateFlow<List<AppModel>>(listOf())

    val enabledFilterMode = MutableStateFlow<FilterMode.EnabledFilterMode>(FilterMode.EnabledFilterMode.ShowAll)
    val exportedFilterMode = MutableStateFlow<FilterMode.ExportedFilterMode>(FilterMode.ExportedFilterMode.ShowAll)
    val permissionFilterMode = MutableStateFlow<FilterMode.PermissionFilterMode>(FilterMode.PermissionFilterMode.ShowAll)

    val query = MutableStateFlow("")
    val isQueryValidRegex = derived {
        get(query).run {
            try {
                Regex(this)
                true
            } catch (e: PatternSyntaxException) {
                false
            }
        }
    }

    val progress = MutableStateFlow<Float?>(null)

    val useRegex = MutableStateFlow(false)
    val includeComponents = MutableStateFlow(true)

    val isSearching = MutableStateFlow(false)

    private val hasFilters: Boolean
        get() = query.value.isNotBlank() ||
                enabledFilterMode.value != FilterMode.EnabledFilterMode.ShowAll ||
                exportedFilterMode.value != FilterMode.ExportedFilterMode.ShowAll ||
                permissionFilterMode.value != FilterMode.PermissionFilterMode.ShowAll

    suspend fun update() = coroutineScope {
        val apps = apps.value.toList()
        val hasFilters = hasFilters
        val isSearching = isSearching.value

        launch(Dispatchers.IO) {
            if (hasFilters || isSearching) {
                val total = apps.sumOf { it.totalUnfilteredSize }
                val current = AtomicInteger(0)
                val lastUpdateTime = AtomicLong(0L)

                apps.forEachParallel {
                    it.loadEverything(true) { _, _ ->
                        val oldCurrent = current.get()
                        val newCurrent = current.incrementAndGet()

                        val oldProgress = (oldCurrent / total.toFloat() * 100f).toInt() / 100f
                        val newProgress = (newCurrent / total.toFloat() * 100f).toInt() / 100f

                        val newUpdateTime = System.currentTimeMillis()

                        if (newProgress > oldProgress && newUpdateTime - 10 > lastUpdateTime.get()) {
                            lastUpdateTime.set(newUpdateTime)

                            progress.value = newProgress
                        }
                    }
                    it.onFilterChange(true)
                }
            } else {
                apps.forEachParallel {
                    it.onFilterChange(false)
                }
            }

            val filtered = if (hasFilters) {
                apps.filter { app ->
                    matches(app)
                }
            } else {
                apps
            }

            val sorted = filtered.sortedBy { it.label.toString().lowercase() }

            filteredApps.value = sorted
            progress.value = null
        }
    }

    private fun matches(data: AppModel): Boolean {
        val query = query.value

        if (query.isBlank()) return true

        val activityFilterEmpty = data.filteredActivities.value.isEmpty()
        val serviceFilterEmpty = data.filteredServices.value.isEmpty()
        val receiverFilterEmpty = data.filteredReceivers.value.isEmpty()

        if (includeComponents.value && (!activityFilterEmpty || !serviceFilterEmpty || !receiverFilterEmpty)) return true

        val advancedMatch = AdvancedSearcher.matchesHasPermission(query, data)
                || AdvancedSearcher.matchesRequiresPermission(query, data)
                || AdvancedSearcher.matchesDeclaresPermission(query, data)
                || AdvancedSearcher.matchesRequiresFeature(query, data)

        if (advancedMatch) return true

        if (useRegex.value && isQueryValidRegex.value) {
            if (Regex(query).run {
                    containsMatchIn(data.info.packageName)
                            || containsMatchIn(data.label)
                }) {
                return true
            }
        } else {
            if (data.label.contains(query, true)
                || data.info.packageName.contains(query, true)) {
                return true
            }
        }

        return false
    }
}