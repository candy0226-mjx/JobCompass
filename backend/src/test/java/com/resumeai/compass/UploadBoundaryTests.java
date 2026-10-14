package com.resumeai.compass;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.resumeai.infrastructure.document.DocumentTextExtractor;
import com.resumeai.infrastructure.storage.ObjectStorageService;
class UploadBoundaryTests {
    byte[] pdf(int pages) throws Exception {
        try(var document=new PDDocument();var output=new ByteArrayOutputStream()) {
            for(int i=0;i<pages;i++) document.addPage(new PDPage());document.save(output);return output.toByteArray();
        }
    }
    @Test void scanPdfAndOversizedPageCountDoNotWriteStorageOrRows() throws Exception {
        var snapshots=mock(SnapshotService.class);var extractor=mock(DocumentTextExtractor.class);var storage=mock(ObjectStorageService.class);
        var controller=new SnapshotController(snapshots,extractor,storage);
        when(extractor.extract(any(),anyString(),anyString())).thenReturn(" ");
        assertThatThrownBy(()->controller.upload(new MockMultipartFile("file","scan.pdf","application/pdf",pdf(1)),"扫描简历")).isInstanceOf(CompassException.class);
        assertThatThrownBy(()->controller.upload(new MockMultipartFile("file","long.pdf","application/pdf",pdf(11)),"长简历")).isInstanceOf(CompassException.class);
        assertThatThrownBy(()->controller.upload(new MockMultipartFile("file","fake.pdf","application/pdf","not pdf".getBytes()),"伪文件")).isInstanceOf(CompassException.class);
        verifyNoInteractions(storage,snapshots);
    }
    @Test void failedDatabaseWriteCompensatesUploadedObject() throws Exception {
        var snapshots=mock(SnapshotService.class);var extractor=mock(DocumentTextExtractor.class);var storage=mock(ObjectStorageService.class);
        when(extractor.extract(any(),anyString(),anyString())).thenReturn("有效正文");
        when(snapshots.createUploaded(anyString(),anyString(),anyString(),anyString())).thenThrow(new IllegalStateException("database unavailable"));
        var controller=new SnapshotController(snapshots,extractor,storage);
        assertThatThrownBy(()->controller.upload(new MockMultipartFile("file","resume.pdf","application/pdf",pdf(1)),"简历")).isInstanceOf(IllegalStateException.class);
        verify(storage).put(anyString(),any(),anyLong(),eq("application/pdf"));verify(storage).delete(anyString());
    }
}
