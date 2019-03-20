//
//  PolarTableViewCell.swift
//  PolarSDK-Exercise-Demo
//
//  Created by Mikko Jokinen on 26/11/2018.
//  Copyright © 2018 Polar. All rights reserved.
//

import UIKit

class PolarTableViewCell: UITableViewCell {

    @IBOutlet weak var infoLabel: UILabel!
    override func awakeFromNib() {
        super.awakeFromNib()
        // Initialization code
    }

    override func setSelected(_ selected: Bool, animated: Bool) {
        super.setSelected(selected, animated: animated)

        // Configure the view for the selected state
    }

}
