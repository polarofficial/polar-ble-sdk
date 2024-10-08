//  Copyright © 2023 Polar. All rights reserved.
import Foundation

/// Polar recording secret is used to encrypt the recording.
public struct PolarRecordingSecret {
    
    /// Secret key of size 16 bytes. Supported encryption is AES_128
    /// Please do note that iOS keychain does not support storing AES ciphers. See https://opensource.apple.com/source/Security/Security-58286.41.2/keychain/SecItem.h.auto.html
    let key: Data
    
    public init(key: Data) throws {
        guard (key.count == 16) else {
            throw PolarErrors.invalidArgument(description: "key must be size of 16 bytes (128bits), was \(key.count)")
        }
        
        self.key = key
    }
}

