import SwiftUI
import MobileCoreServices
import UniformTypeIdentifiers

struct FilePicker: UIViewControllerRepresentable {
    
    var types: [UTType]
    var asCopy: Bool
    var multiSelection: Bool = false
    var picked: (([URL]) -> Void)?
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: self.types, asCopy: asCopy)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    class Coordinator: NSObject, UIDocumentPickerDelegate {
        var parent: FilePicker
        
        init(_ parent: FilePicker) {
            self.parent = parent
        }
        
        @available(iOS 11.0, *)
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            let selectedFiles = parent.multiSelection ? urls : urls.dropLast(urls.count - 1)
            parent.picked?(selectedFiles)
        }
        
        @available(iOS 8.0, *)
        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
            parent.picked?([])
            
        }
        
        @available(iOS, introduced: 8.0, deprecated: 11.0)
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentAt url: URL) {
            parent.picked?([url])
        }
    }
}
