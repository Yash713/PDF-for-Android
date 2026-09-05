package com.pdfmaster.app.di

import com.pdfmaster.app.data.repository.PdfEngineImpl
import com.pdfmaster.app.data.repository.TesseractOcrEngine
import com.pdfmaster.app.domain.repository.OcrEngine
import com.pdfmaster.app.domain.repository.PdfEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindPdfEngine(impl: PdfEngineImpl): PdfEngine

    @Binds
    abstract fun bindOcrEngine(impl: TesseractOcrEngine): OcrEngine
}
