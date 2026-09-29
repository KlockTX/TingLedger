package com.klocktx.moneyblocks.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.data.repo.SettingsRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 账单上限走**真 DataStore**。
 *
 * `BillCapsTest` 证的是编解码本身，`FakeSettings` 又是内存版，两头都不碰偏好文件。
 * 这条要钉的是线上那条路：写进去的确实读得回来、`setBillCap` 是合并而不是整表覆盖、
 * 传 0 真的等于清掉。
 *
 * 用一组只在本类出现的账单 id，不按"读之前必须是空表"下断言——偏好文件在同一进程的
 * 多个测试之间是复用的，依赖干净起点会让测试顺序一变就假失败。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BillCapStoreTest {

    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        settings = SettingsRepositoryImpl(context)
    }

    private suspend fun caps() = settings.billCaps.first()

    @Test
    fun capsRoundTripThroughThePreferencesFile() = runTest {
        settings.setBillCap(901L, 12_345_600L.cents)
        settings.setBillCap(902L, 100L.cents)

        val read = caps()
        assertEquals(12_345_600L, read[901L]?.value)
        assertEquals(100L, read[902L]?.value)
    }

    @Test
    fun settingOneCapKeepsTheOthers() = runTest {
        settings.setBillCap(911L, 1L.cents)
        settings.setBillCap(912L, 2L.cents)
        settings.setBillCap(911L, 11L.cents)

        val read = caps()
        assertEquals("同一账单改上限不该多出一条", 11L, read[911L]?.value)
        assertEquals("改一条不该把别条挤掉", 2L, read[912L]?.value)
    }

    /** 编辑器里把上限清空，走的就是"写 0"这条：0 不能被存成一个真的上限。 */
    @Test
    fun writingZeroRemovesTheCap() = runTest {
        settings.setBillCap(921L, 500_00L.cents)
        assertEquals(500_00L, caps()[921L]?.value)

        settings.setBillCap(921L, 0L.cents)
        // 只断言这一个 id：偏好文件在同一进程内是跨测试复用的，比 size 会在换顺序时假失败
        assertNull(caps()[921L])
    }

    @Test
    fun dropOnlyTakesThatBillAndReplaceTakesTheWholeTable() = runTest {
        settings.setBillCap(931L, 10L.cents)
        settings.setBillCap(932L, 20L.cents)

        settings.dropBillCap(931L)
        assertEquals(mapOf(932L to 20L.cents), caps().filterKeys { it == 931L || it == 932L })

        settings.replaceBillCaps(mapOf(941L to 30L.cents))
        assertEquals(30L, caps()[941L]?.value)
        assertEquals("整表替换之后旧 id 不该还在", null, caps()[932L])
    }
}
