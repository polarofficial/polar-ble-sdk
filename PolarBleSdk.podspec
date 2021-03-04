Pod::Spec.new do |s|  
    s.name              = 'PolarBleSdk'
    s.version           = '3.0.2'
    s.summary           = 'SDK for Polar sensors'
    s.homepage          = 'https://github.com/polarofficial/polar-ble-sdk'
    s.authors           = 'Polar Electro'
    s.license           = { :type => 'Custom', :file => 'Polar_SDK_License.txt' }
    s.platform          = :ios
    s.swift_versions    = '5.0'
    s.source            = { :git => 'https://github.com/polarofficial/polar-ble-sdk.git', :tag => '3.0.2' }
    s.ios.deployment_target = '12.0'
    s.ios.vendored_frameworks = 'polar-sdk-ios/PolarBleSdk.xcframework'
    s.dependency 'RxSwift', '~> 6'
end 
