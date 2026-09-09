//  Copyright © 2026 Polar. All rights reserved.

import SwiftUI
import PolarBleSdk

struct GenericApiView: View {
    
    private struct ShownFileList: Identifiable {
        let fileList: [String]
        let title: String
        var id: String { return "\(fileList)" }
    }
    
    private struct ShownFileData: Identifiable {
        let fileData: Data
        let title: String
        var id: String { return "\(fileData)" }
    }
    
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    
    @State private var pathWhenList: String = ""
    @State private var pathWhenRead: String = ""
    @State private var pathWhenWrite: String = ""
    @State private var pathWhenDelete: String = ""
    @State private var pathWhenCreateFolder: String = ""
    @State private var binaryData: String = ""
    @State private var toastTimeOut: Double = 10.0
    
    @State private var recurseDeep: Bool = false
    @State private var shownFileList: ShownFileList? = nil
    @State private var shownFileData: ShownFileData? = nil
    @State private var toast: String? = nil
    @State private var genericApiFileOperationInProgress: Bool = false
    
    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                
                Text("Low level API functions")
                    .font(.title3).fontWeight(.semibold)
                
                Spacer()
                
                VStack(spacing: 3) {
                    Text("List files in device")
                        .font(.subheadline).fontWeight(.semibold)
                    
                    TextField("Path to directory", text: $pathWhenList)
                        .keyboardType(.alphabet)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    
                    HStack {
                        Toggle("Recurse deep", isOn: $recurseDeep)
                            .toggleStyle(SwitchToggleStyle(tint: .blue))
                            .frame(width: 200)
                        
                        Button("List") {
                            Task {
                                do {
                                    toastTimeOut = 120
                                    toast = "Fetching file list for path \(pathWhenList)"
                                    genericApiFileOperationInProgress = true
                                    try await bleSdkManager.listFiles(directoryPath: pathWhenList, recurseDeep: recurseDeep)
                                    genericApiFileOperationInProgress = false
                                    toastTimeOut = 10
                                } catch let err {
                                    toastTimeOut = 10
                                    toast = "Fetching file list failed with error \(err)"
                                    genericApiFileOperationInProgress = false
                                    AppLogger.log("Listing files for path \(pathWhenList) failed with error \(err)")
                                }
                                
                                genericApiFileOperationInProgress = false
                                shownFileList = ShownFileList(fileList: bleSdkManager.genericApiFileList, title: pathWhenList)
                            }
                        }
                        .buttonStyle(PrimaryButtonStyle(buttonState: ButtonState.released))
                        .disabled(genericApiFileOperationInProgress)
                    }
                }
                .sheet(
                    item: $shownFileList,
                    content: { shownFileList in
                        NavigationView {
                            TextViewerView(
                                title: shownFileList.title,
                                text: shownFileList.fileList.joined(separator: "\n")
                            )
                            .toolbar {
                                ToolbarItem(placement: .navigationBarTrailing) {
                                    Button(action: {
                                        self.shownFileList = nil
                                    }) {
                                        Image(systemName: "xmark.circle.fill")
                                            .foregroundColor(.blue)
                                    }.accessibility(identifier: "Close \(shownFileList.title)")
                                }
                            }
                        }
                    })
                
                Spacer()
                
                VStack(spacing: 3) {
                    Text("Read a file in device")
                        .font(.subheadline).fontWeight(.semibold)
                    
                    TextField("Path to file", text: $pathWhenRead)
                        .keyboardType(.alphabet)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    
                    Button("Read") {
                        toast = "Fetching file"
                        Task {
                            genericApiFileOperationInProgress = true
                            
                            do {
                                genericApiFileOperationInProgress = true
                                toast = "Reading file \(pathWhenRead)"
                                try await bleSdkManager.readFile(filePath: pathWhenRead)
                                genericApiFileOperationInProgress = false
                            } catch let err {
                                genericApiFileOperationInProgress = false
                                toast = "Reading file \(pathWhenRead) failed with error \(err)"
                                AppLogger.log("Reading file \(pathWhenRead) failed with error \(err)")
                            }
                            
                            genericApiFileOperationInProgress = false
                            shownFileData = ShownFileData(fileData: bleSdkManager.genericApiFileData, title: pathWhenRead)
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle(buttonState: ButtonState.released))
                    .disabled(genericApiFileOperationInProgress)
                }
                .sheet(
                    item: $shownFileData,
                    content: { shownFileData in
                        NavigationView {
                            TextViewerView(
                                title: shownFileData.title,
                                text: String(data: shownFileData.fileData, encoding: .utf8) ?? "No data found"
                            )
                            .toolbar {
                                ToolbarItem(placement: .navigationBarTrailing) {
                                    Button(action: {
                                        self.shownFileData = nil
                                    }) {
                                        Image(systemName: "xmark.circle.fill")
                                            .foregroundColor(.blue)
                                    }.accessibility(identifier: "Close \(shownFileData.title)")
                                }
                            }
                        }
                    })
                
                Spacer()
                VStack(spacing: 3) {
                    Text("Write a file to device")
                        .font(.subheadline).fontWeight(.semibold)
                    
                    TextField("Path to file", text: $pathWhenWrite)
                        .keyboardType(.alphabet)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    
                    TextEditor(text: $binaryData)
                        .frame(height: 100)
                        .keyboardType(.alphabet)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.gray.opacity(0.2), lineWidth: 1)
                        )
                        .padding([.horizontal], 4)
                        .toolbar {
                            ToolbarItemGroup(placement: .keyboard) {
                                Button("Dismiss keyboard") {
                                    hideKeyboard()
                                }
                                .buttonStyle(.borderedProminent)
                            }
                        }
                    
                    Button("Write") {
                        toast = "Fetching file list"
                        Task {
                            if (!binaryData.isEmpty) {
                                toast = "Writing data to \(pathWhenWrite)"
                                do {
                                    genericApiFileOperationInProgress = true
                                    toast = "Writing data to \(pathWhenWrite)"
                                    try await bleSdkManager.writeFile(filePath: pathWhenWrite, fileData: binaryData.data(using: .utf8)!)
                                    genericApiFileOperationInProgress = false
                                    toast = "Data written to \(pathWhenWrite)"
                                } catch let err {
                                    genericApiFileOperationInProgress = false
                                    toast = "Writing data to \(pathWhenWrite) failed with error \(err)"
                                    AppLogger.log("Writing file to path \(pathWhenWrite) failed with error \(err)")
                                }
                            } else {
                                toast = "Empty file data!"
                            }
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle(buttonState: ButtonState.released))
                    .disabled(genericApiFileOperationInProgress)
                }
                
                VStack(spacing: 3) {
                    Text("Delete a file in device")
                        .font(.subheadline).fontWeight(.semibold)
                    
                    TextField("Path to file", text: $pathWhenDelete)
                        .keyboardType(.alphabet)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    
                    Button("Delete") {
                        toast = "Fetching file"
                        Task {
                            genericApiFileOperationInProgress = true
                            do {
                                toast = "Deleting from \(pathWhenDelete)"
                                try await bleSdkManager.deleteFile(filePath: pathWhenDelete)
                                toast = "Deleted \(pathWhenDelete)"
                            } catch let err {
                                genericApiFileOperationInProgress = false
                                toast = "Deleting file from path \(pathWhenDelete) failed with error \(err)"
                                AppLogger.log("Deleting file from path \(pathWhenDelete) failed with error \(err)")
                            }
                            genericApiFileOperationInProgress = false
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle(buttonState: ButtonState.released))
                    .disabled(genericApiFileOperationInProgress)
                }

                VStack(spacing: 3) {
                    Text("Create a folder on device")
                        .font(.subheadline).fontWeight(.semibold)

                    TextField("Folder path to create", text: $pathWhenCreateFolder)
                        .keyboardType(.alphabet)
                        .textFieldStyle(RoundedBorderTextFieldStyle())

                    Button("Create") {
                        Task {
                            genericApiFileOperationInProgress = true
                            do {
                                toast = "Creating folder \(pathWhenCreateFolder)"
                                try await bleSdkManager.createFolder(folderPath: pathWhenCreateFolder)
                                toast = "Folder \(pathWhenCreateFolder) created successfully"
                            } catch let err {
                                toast = "Creating folder \(pathWhenCreateFolder) failed with error \(err)"
                                AppLogger.log("Creating folder \(pathWhenCreateFolder) failed with error \(err)")
                            }
                            genericApiFileOperationInProgress = false
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle(buttonState: ButtonState.released))
                    .disabled(genericApiFileOperationInProgress)
                }
            }
            .padding(16)
            .overlay(alignment: .bottom) {
                if let toast {
                    Text(toast)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: Capsule())
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                        .padding(.bottom, 24)
                        .onAppear {
                            DispatchQueue.main.asyncAfter(deadline: .now() + toastTimeOut) {
                                withAnimation { self.toast = nil }
                            }
                        }
                }
            }
            .animation(.easeInOut, value: toast)
            
        }
        .navigationViewStyle(.stack)
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                withAnimation { self.toast = nil }
            }
        }.navigationTitle("Generic API functions")
    }
}

fileprivate extension View {
    func hideKeyboard() {
        let resign = #selector(UIResponder.resignFirstResponder)
        UIApplication.shared.sendAction(resign, to: nil, from: nil, for: nil)
    }
}
