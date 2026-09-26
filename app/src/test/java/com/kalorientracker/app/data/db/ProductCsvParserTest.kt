package com.kalorientracker.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductCsvParserTest {
    @Test
    fun parsesAPlainLine() {
        val food = ProductCatalogImporter.parseLine("4001686301203,Goldbären,Haribo,343,6.9,77,0.5,0,46,0.1,0.07,100")!!
        assertEquals("4001686301203", food.barcode)
        assertEquals("Goldbären", food.name)
        assertEquals("Haribo", food.brand)
        assertEquals(343.0, food.per100g.kcal, 0.0)
        assertEquals(100.0, food.servingGrams!!, 0.0)
    }

    @Test
    fun parsesQuotedNamesWithCommas() {
        val food = ProductCatalogImporter.parseLine("""4000417025005,"Nuss-Nougat, extra",Nutella,539,6.3,57.5,30.9,0,56.3,10.6,0.107,15""")!!
        assertEquals("Nuss-Nougat, extra", food.name)
    }

    @Test
    fun rejectsBrokenOrImplausibleLines() {
        assertNull(ProductCatalogImporter.parseLine(""))
        assertNull(ProductCatalogImporter.parseLine("abc,Name,,100,1,1,1,0,0,0,0,100"))
        assertNull(ProductCatalogImporter.parseLine("4001686301203,Name,,0,0,0,0,0,0,0,0,100"))
        assertNull(ProductCatalogImporter.parseLine("4001686301203,Name,,5000,1,1,1,0,0,0,0,100"))
    }

    @Test
    fun rejectsLineWithUnterminatedQuote() {
        // An embedded newline inside a quoted field, read line by line, splits one CSV row into
        // two lines: the first keeps the opening quote but never sees a closing one. Without this
        // check the last field here would silently parse as "100" instead of failing the row.
        assertNull(
            ProductCatalogImporter.parseLine(
                "4001686301203,Goldbären,Haribo,343,6.9,77,0.5,0,46,0.1,0.07,\"100",
            ),
        )
    }
}
