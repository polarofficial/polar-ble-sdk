
import Foundation

public class BleRefApiVersion {
    static let PATTERN = "^(0|[1-9][0-9]*)(\\.)(0|[1-9][0-9]*)(.*)$"
    
    public static func versionString() -> String {
        return "\(major()).\(minor()).\(patch())"
    }
    
    public static func major() -> Int {
        let matches = matchesForRegexInText(PATTERN,text: IOS_COMMUNICATIONS_VERSION)
        if matches.count != 0 {
            return Int(matches.first!)!
        }
        return 0
    }
    
    public static func minor() -> Int {
        let matches = matchesForRegexInText(PATTERN,text: IOS_COMMUNICATIONS_VERSION)
        if matches.count > 2 {
            return Int(matches[2])!
        }
        return 0
    }
    
    public static func patch() -> Int {
        let matches = matchesForRegexInText(PATTERN,text: IOS_COMMUNICATIONS_VERSION)
        if matches.count > 3 {
            // patch present
            let patch = matches[3].components(separatedBy: "-")
            if patch.count > 1 {
                return Int(patch[1])!
            }
        }
        return 0
    }
    
    static func matchesForRegexInText(_ regex: String, text: String) -> [String] {
        do {
            let regex = try NSRegularExpression(pattern: regex, options: [])
            let nsString = text as NSString
            let results = regex.matches(in: text, options: [], range: NSMakeRange(0, nsString.length))
            var match = [String]()
            for result in results {
                for i in 1..<result.numberOfRanges {
                    match.append(nsString.substring( with: result.range(at: i) ))
                }
            }
            return match
        } catch let error as NSError {
            print("invalid regex: \(error.localizedDescription)")
            return []
        }
    }
}
