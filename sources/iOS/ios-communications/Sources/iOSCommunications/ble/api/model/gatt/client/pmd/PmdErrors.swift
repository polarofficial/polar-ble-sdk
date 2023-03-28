//  Copyright © 2023 Polar. All rights reserved.

import Foundation

public enum BlePmdError: Error {
    case controlPointRequestFailed(errorCode: Int, description:String)
    case bleOnlineStreamClosed(description:String)
}
