package com.amcentral365.service.mergedata

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

import java.util.UUID
import java.util.stream.Stream

import com.amcentral365.service.dao.Asset
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class MergeAssetsTest {

    val mergeAssets = MergeAssets(".")
    val ANY   = UUID.fromString("deadbeef-aced-acdc-cafe-000000000000")
    val uuid1 = UUID.fromString("deadbeef-aced-acdc-cafe-111111111111")
    val uuid2 = UUID.fromString("deadbeef-aced-acdc-cafe-222222222222")

    @ParameterizedTest(name = "test {index}: {0}")
    @MethodSource("checkMergeParams")
    fun checkMergeAssetAgainstDb(testName: String,
                                 mergeId: UUID?, mergeName: String?,
                                 dbId: UUID?, dbName: String?,
                                 expectedId: UUID?, expectedName: String?,
                                 expectedMsgSubstr: String?

    ) {
        val mergeAsset = Asset(mergeId, mergeName)
        val retMsg = mergeAssets.checkMergeAssetAgainstDb(mergeAsset, dbId, dbName)

        if( expectedMsgSubstr == null )
            assertNull(retMsg)
        else {
            assertNotNull(retMsg)
            assertTrue(retMsg!!.contains(expectedMsgSubstr), "actual msg: $retMsg")
        }

        if( mergeId == ANY )
            assertNotNull(mergeAsset.assetId)
        else
            assertEquals(expectedId, mergeAsset.assetId)

        assertEquals(expectedName, mergeAsset.name)
    }


    fun checkMergeParams(): Stream<Arguments> = Stream.of(
        Arguments.of("error: all nulls",          null,null,  null,null,  null,  null, "neither asset id nor name")
       ,Arguments.of("insert w/o name",          uuid1,null,  null,null,  uuid1, null, "name was not provided")
       ,Arguments.of("insert with generated id",   ANY, "x",  null,null,  ANY,  "x",    null)
       ,Arguments.of("insert with name",         uuid1, "x",  null,null,  uuid1,"x",    null)

       ,Arguments.of("update w/o id",             null,"x",   uuid1,"x",  uuid1, "x",  null)
       ,Arguments.of("update w/ coding err",     uuid1,null,  uuid1,"x",  uuid1,null, "was somehow fetched even though asset name wasn't provided")
       ,Arguments.of("update w/o name",          uuid1,null,   null,"x",  uuid1, "x",  null)
       ,Arguments.of("update rename",            uuid1,"y",   uuid1,"x",  uuid1, "y",  null)
       ,Arguments.of("update w/ id conflict",    uuid1,"x",   uuid2,"x",  uuid1, "x", "does not match one in the mergefile")
       ,Arguments.of("update no change",         uuid1,"x",   uuid1,"x",  uuid1, "x",  null)
    )
}
