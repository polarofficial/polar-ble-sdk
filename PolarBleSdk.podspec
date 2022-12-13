Pod::Spec.new do |s|  
    s.name              = 'PolarBleSdk'
    s.version           = '4.0.0-beta0'
    s.summary           = 'SDK for Polar sensors'
    s.homepage          = 'https://github.com/polarofficial/polar-ble-sdk'
    s.license           = { :type => 'Custom', :file => 'Polar_SDK_License.txt' }
    s.authors           = 'Polar Electro Oy'  
    s.swift_versions    = '5.0'
    s.cocoapods_version = '>= 1.10'
    s.source            = { :git => 'https://github.com/polarofficial/polar-ble-sdk.git', :tag => s.version.to_s }

    s.ios.deployment_target = '13.0'
    
    s.source_files = 'sources/iOS/ios-communications/Sources/**/*.swift'

    s.dependency 'RxSwift', '~> 6.5.0'
    s.dependency 'SwiftProtobuf', '~> 1.0'
end 
